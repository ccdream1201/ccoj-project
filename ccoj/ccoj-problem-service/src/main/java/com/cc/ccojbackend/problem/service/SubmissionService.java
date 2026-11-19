package com.cc.ccojbackend.problem.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.model.problem.ProblemCase;
import com.cc.ccojbackend.model.problem.JudgeConfig;
import com.cc.ccojbackend.model.submission.*;
import com.cc.ccojbackend.problem.mapper.ProblemMapper;
import com.cc.ccojbackend.problem.mapper.SubmissionMapper;
import com.cc.ccojbackend.problem.model.entity.Problem;
import com.cc.ccojbackend.problem.model.entity.Submission;
import java.util.List;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionService {
    private static final List<Long> RETRY_DELAYS_SECONDS = List.of(5L, 30L, 120L);

    private final SubmissionMapper submissions;
    private final ProblemMapper problems;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;
    private final JsonMapper json;

    @Value("${ccoj.protection.judge-retry-enabled:true}")
    private boolean retryEnabled;

    public Long submit(Long problemId, Long userId, String language, String sourceCode) {
        if (!"java".equalsIgnoreCase(language)) throw new BusinessException(ResultCode.PARAMS_ERROR);
        Long id = transactions.execute(transaction -> {
            // Lock the parent row before the submission insert acquires its foreign-key lock.
            int updated = problems.update(null, Wrappers.<Problem>lambdaUpdate()
                    .eq(Problem::getId, problemId)
                    .eq(Problem::getStatus, 1)
                    .setSql("submission_count = submission_count + 1"));
            if (updated != 1) throw new BusinessException(ResultCode.NOT_FOUND);

            var submission = new Submission();
            submission.setProblemId(problemId);
            submission.setUserId(userId);
            submission.setLanguage("JAVA");
            submission.setSourceCode(sourceCode);
            submission.setStatus(SubmissionStatus.PENDING.getValue());
            submission.setRetryAt(new Date());
            submissions.insert(submission);
            return submission.getId();
        });

        return id;
    }

    private boolean publish(long id, int attempt) {
        return publish(JudgeQueue.NAME, Long.toString(id), id, attempt);
    }

    private boolean publish(String queue, String payload, long id, int attempt) {
        try {
            CorrelationData correlation = new CorrelationData();
            rabbit.convertAndSend("", queue, payload, message -> {
                message.getMessageProperties().setHeader(JudgeQueue.ATTEMPT_HEADER, attempt);
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                if (JudgeQueue.FAILURE_QUEUE.equals(queue)) {
                    message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    message.getMessageProperties().setMessageId("judge-failed:" + id + ":" + attempt);
                }
                return message;
            }, correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            return confirm.isAck() && correlation.getReturned() == null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (AmqpException | ExecutionException | TimeoutException exception) {
            log.warn("Could not confirm publication for submission {} attempt {}", id, attempt, exception);
        }
        return false;
    }

    public JudgeTask claim(long id, int attempt) {
        return transactions.execute(transaction -> {
            int claimed = submissions.update(null, Wrappers.<Submission>lambdaUpdate()
                    .eq(Submission::getId, id)
                    .eq(Submission::getStatus, SubmissionStatus.PENDING.getValue())
                    .eq(Submission::getJudgeAttempt, attempt)
                    .and(due -> due.isNull(Submission::getRetryAt).or().le(Submission::getRetryAt, new Date()))
                    .set(Submission::getRetryAt, null)
                    .set(Submission::getStatus, SubmissionStatus.RUNNING.getValue()));
            if (claimed == 0) return null;
            var submission = submissions.selectById(id);
            var problem = problems.selectById(submission.getProblemId());
            if (problem == null) {
                changeStatus(id, attempt, SubmissionStatus.RUNNING, SubmissionStatus.SYSTEM_FAILED);
                return null;
            }
            List<ProblemCase> cases;
            JudgeConfig config;
            try {
                cases = json.readValue(problem.getJudgeCases(), new TypeReference<>() {});
                config = problem.getJudgeConfig() == null ? null
                        : json.readValue(problem.getJudgeConfig(), JudgeConfig.class);
            } catch (RuntimeException exception) {
                log.warn("Invalid judge cases for submission {}", id, exception);
                changeStatus(id, attempt, SubmissionStatus.RUNNING, SubmissionStatus.SYSTEM_FAILED);
                return null;
            }
            if (cases == null || cases.isEmpty() || cases.stream()
                    .anyMatch(test -> test == null || test.getInput() == null || test.getOutput() == null)) {
                changeStatus(id, attempt, SubmissionStatus.RUNNING, SubmissionStatus.SYSTEM_FAILED);
                return null;
            }
            return new JudgeTask(submission.getSourceCode(), cases,
                    config == null ? null : config.getTimeLimit(),
                    config == null ? null : config.getMemoryLimit());
        });
    }

    public void finish(long id, int attempt, JudgeOutcome outcome) {
        if (outcome.status() == null || outcome.status() == SubmissionStatus.PENDING
                || outcome.status() == SubmissionStatus.RUNNING || outcome.passedCaseCount() < 0
                || outcome.totalCaseCount() < outcome.passedCaseCount()) {
            throw new BusinessException(ResultCode.PARAMS_ERROR);
        }
        transactions.executeWithoutResult(transaction -> {
            int updated = submissions.update(null,
                    Wrappers.<Submission>lambdaUpdate()
                    .eq(Submission::getId, id)
                    .eq(Submission::getStatus, SubmissionStatus.RUNNING.getValue())
                    .eq(Submission::getJudgeAttempt, attempt)
                    .set(outcome.status() == SubmissionStatus.SYSTEM_FAILED,
                            Submission::getPendingFailureReason, "JUDGE_SYSTEM_ERROR")
                    .set(Submission::getStatus, outcome.status().getValue())
                    .set(Submission::getPassedCaseCount, outcome.passedCaseCount())
                    .set(Submission::getTotalCaseCount, outcome.totalCaseCount())
                    .set(Submission::getCompileOutput, outcome.compileOutput()));
            if (updated == 1 && outcome.status() == SubmissionStatus.ACCEPTED) {
                var submission = submissions.selectById(id);
                problems.update(null, Wrappers.<Problem>lambdaUpdate()
                        .eq(Problem::getId, submission.getProblemId())
                        .setSql("accepted_count = accepted_count + 1"));
            }
        });
    }

    @Scheduled(fixedDelay = 60000)
    public void failStalledSubmissions() {
        failStalled(SubmissionStatus.PENDING, Duration.ofHours(1));
        var expired = submissions.selectList(Wrappers.<Submission>lambdaQuery()
                .select(Submission::getId, Submission::getJudgeAttempt, Submission::getUpdatedAt)
                .eq(Submission::getStatus, SubmissionStatus.RUNNING.getValue())
                .lt(Submission::getUpdatedAt, Date.from(Instant.now().minus(Duration.ofMinutes(5))))
                .orderByAsc(Submission::getUpdatedAt, Submission::getId)
                .last("LIMIT 100"));
        for (var submission : expired) {
            scheduleRetry(submission.getId(), submission.getJudgeAttempt(), submission.getUpdatedAt());
        }
    }

    private void failStalled(SubmissionStatus status, Duration age) {
        int count = submissions.update(null, Wrappers.<Submission>lambdaUpdate()
                .eq(Submission::getStatus, status.getValue())
                .lt(Submission::getUpdatedAt, Date.from(Instant.now().minus(age)))
                .set(Submission::getRetryAt, null)
                .set(Submission::getPendingFailureReason, "PENDING_EXPIRED")
                .set(Submission::getStatus, SubmissionStatus.SYSTEM_FAILED.getValue()));
        if (count > 0) log.warn("Marked {} stalled {} submissions as SYSTEM_FAILED", count, status);
    }

    public void scheduleRetry(long id, int attempt) {
        if (attempt < 0) throw new BusinessException(ResultCode.PARAMS_ERROR);
        scheduleRetry(id, attempt, null);
    }

    private void scheduleRetry(long id, int attempt, Date observedUpdatedAt) {
        var update = Wrappers.<Submission>lambdaUpdate()
                .eq(Submission::getId, id)
                .eq(Submission::getStatus, SubmissionStatus.RUNNING.getValue())
                .eq(Submission::getJudgeAttempt, attempt)
                .eq(observedUpdatedAt != null, Submission::getUpdatedAt, observedUpdatedAt);
        if (!retryEnabled || attempt >= RETRY_DELAYS_SECONDS.size()) {
            submissions.update(null, update
                    .set(Submission::getPendingFailureReason,
                            retryEnabled ? "RETRIES_EXHAUSTED" : "RETRY_DISABLED")
                    .set(Submission::getStatus, SubmissionStatus.SYSTEM_FAILED.getValue()));
            return;
        }

        Date due = Date.from(Instant.now().plusSeconds(RETRY_DELAYS_SECONDS.get(attempt)));
        int scheduled = submissions.update(null, update
                .set(Submission::getStatus, SubmissionStatus.PENDING.getValue())
                .set(Submission::getJudgeAttempt, attempt + 1)
                .set(Submission::getRetryAt, due));
        if (scheduled == 1) log.info("Scheduled retry for submission {} attempt {} at {}", id, attempt + 1, due);
    }

    @Scheduled(fixedDelay = 1000)
    public void publishDueSubmissions() {
        var due = submissions.selectList(Wrappers.<Submission>lambdaQuery()
                .select(Submission::getId, Submission::getJudgeAttempt, Submission::getRetryAt)
                .eq(Submission::getStatus, SubmissionStatus.PENDING.getValue())
                .le(Submission::getRetryAt, new Date())
                .orderByAsc(Submission::getRetryAt, Submission::getId)
                .last("LIMIT 20"));
        for (var submission : due) {
            long id = submission.getId();
            int attempt = submission.getJudgeAttempt();
            if (!publish(id, attempt)) return;
            // A consumer may already have claimed this attempt, or scheduled the next one.
            submissions.update(null, Wrappers.<Submission>lambdaUpdate()
                    .eq(Submission::getId, id)
                    .eq(Submission::getStatus, SubmissionStatus.PENDING.getValue())
                    .eq(Submission::getJudgeAttempt, attempt)
                    .eq(Submission::getRetryAt, submission.getRetryAt())
                    .set(Submission::getRetryAt, null));
        }
    }

    private record FailureNotice(long submissionId, int attempt, String status, String reason) {}

    @Scheduled(fixedDelay = 1000)
    public void publishFailureNotices() {
        var pending = submissions.selectList(Wrappers.<Submission>lambdaQuery()
                .select(Submission::getId, Submission::getJudgeAttempt, Submission::getPendingFailureReason)
                .isNotNull(Submission::getPendingFailureReason)
                .orderByAsc(Submission::getId)
                .last("LIMIT 20"));
        for (var submission : pending) {
            long id = submission.getId();
            int attempt = submission.getJudgeAttempt();
            String reason = submission.getPendingFailureReason();
            String payload = json.writeValueAsString(new FailureNotice(
                    id, attempt, SubmissionStatus.SYSTEM_FAILED.getValue(), reason));
            if (!publish(JudgeQueue.FAILURE_QUEUE, payload, id, attempt)) return;
            submissions.update(null, Wrappers.<Submission>lambdaUpdate()
                    .eq(Submission::getId, id)
                    .eq(Submission::getJudgeAttempt, attempt)
                    .eq(Submission::getPendingFailureReason, reason)
                    .set(Submission::getPendingFailureReason, null));
        }
    }

    private int changeStatus(long id, int attempt, SubmissionStatus from, SubmissionStatus to) {
        return submissions.update(null, Wrappers.<Submission>lambdaUpdate()
                .eq(Submission::getId, id)
                .eq(Submission::getJudgeAttempt, attempt)
                .eq(Submission::getStatus, from.getValue())
                .set(Submission::getRetryAt, null)
                .set(to == SubmissionStatus.SYSTEM_FAILED,
                        Submission::getPendingFailureReason, "INVALID_JUDGE_TASK")
                .set(Submission::getStatus, to.getValue()));
    }
}
