package com.cc.ccojbackend.judge.service;

import com.cc.ccojbackend.client.ProblemJudgeClient;
import com.cc.ccojbackend.model.problem.ProblemCase;
import com.cc.ccojbackend.model.sandbox.ExecuteCodeRequest;
import com.cc.ccojbackend.model.submission.JudgeOutcome;
import com.cc.ccojbackend.model.submission.JudgeQueue;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;

@Component
@Slf4j
public class JudgeConsumer {
    private final ProblemJudgeClient problems;
    private final ProtectedSandbox sandbox;
    private final JudgeEvaluator evaluator;

    private final boolean retryEnabled;

    public JudgeConsumer(ProblemJudgeClient problems, ProtectedSandbox sandbox, JudgeEvaluator evaluator,
                         @Value("${ccoj.protection.judge-retry-enabled:true}") boolean retryEnabled) {
        this.problems = problems;
        this.sandbox = sandbox;
        this.evaluator = evaluator;
        this.retryEnabled = retryEnabled;
    }

    @RabbitListener(queues = JudgeQueue.NAME, ackMode = "MANUAL")
    public void consume(String message,
                        @Header(name = JudgeQueue.ATTEMPT_HEADER, required = false) Integer deliveryAttempt,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        Channel channel) throws IOException {
        long id;
        try {
            id = Long.parseLong(message);
            if (id <= 0) throw new NumberFormatException("submission id must be positive");
        } catch (NumberFormatException exception) {
            log.warn("Rejecting malformed judge message");
            channel.basicReject(deliveryTag, false);
            return;
        }
        int attempt = deliveryAttempt == null ? 0 : deliveryAttempt;
        if (attempt < 0) {
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            handle(id, attempt);
        } catch (RuntimeException failure) {
            log.error("Could not persist judge operation for submission {} attempt {}", id, attempt, failure);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void handle(long id, int attempt) {
        var task = problems.claim(id, attempt);
        if (task == null) return;

        JudgeOutcome outcome;
        try {
            var request = new ExecuteCodeRequest();
            request.setSourceCode(task.sourceCode());
            request.setTimeLimitMs(task.timeLimitMs());
            request.setMemoryLimitMb(task.memoryLimitMb());
            request.setInputList(task.cases().stream().map(ProblemCase::getInput).toList());
            outcome = evaluator.evaluate(task, sandbox.execute(request));
        } catch (ProtectedSandbox.TemporaryFailure failure) {
            log.warn("Sandbox unavailable for submission {} attempt {}: {}", id, attempt, failure.getMessage());
            if (retryEnabled) {
                problems.retry(id, attempt);
                return;
            }
            outcome = evaluator.failed(task.cases().size());
        } catch (RuntimeException exception) {
            log.error("Sandbox execution failed for submission {}", id, exception);
            outcome = evaluator.failed(task.cases().size());
        }
        problems.finish(id, attempt, outcome);
    }
}
