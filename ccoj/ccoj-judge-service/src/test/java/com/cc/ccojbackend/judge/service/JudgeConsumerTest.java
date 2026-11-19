package com.cc.ccojbackend.judge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cc.ccojbackend.client.ProblemJudgeClient;
import com.cc.ccojbackend.model.sandbox.ExecuteCodeResponse;
import com.cc.ccojbackend.model.sandbox.ProcessResult;
import com.cc.ccojbackend.model.submission.JudgeOutcome;
import com.cc.ccojbackend.model.submission.JudgeTask;
import com.rabbitmq.client.Channel;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class JudgeConsumerTest {
    private final List<String> calls = new ArrayList<>();

    @Test
    void confirmsOnlyAfterTheOutcomeIsStored() throws Exception {
        invoke("42", false, false, false);
        assertEquals(List.of("claim:42:2", "sandbox", "finish", "basicAck"), calls);
    }

    @Test
    void confirmsAfterRetryIsScheduled() throws Exception {
        invoke("42", true, false, false);
        assertEquals(List.of("claim:42:2", "sandbox", "retry", "basicAck"), calls);
    }

    @Test
    void rejectsPersistenceFailureWithoutAcknowledgingIt() throws Exception {
        invoke("42", true, true, false);
        assertEquals(List.of("claim:42:2", "sandbox", "retry", "basicNack"), calls);
    }

    @Test
    void acknowledgesAnObsoleteMessageWithoutCallingTheSandbox() throws Exception {
        invoke("42", false, false, true);
        assertEquals(List.of("claim:42:2", "basicAck"), calls);
    }

    @Test
    void rejectsMalformedMessagesWithoutCallingTheProblemService() throws Exception {
        invoke("bad", false, false, false);
        assertEquals(List.of("basicReject"), calls);
    }

    private void invoke(String id, boolean temporaryFailure, boolean persistenceFailure, boolean obsolete)
            throws Exception {
        ProblemJudgeClient problems = new ProblemJudgeClient() {
            @Override
            public JudgeTask claim(long submissionId, int attempt) {
                calls.add("claim:" + submissionId + ":" + attempt);
                return obsolete ? null : new JudgeTask("class Main {}", List.of(), null, null);
            }

            @Override
            public void finish(long submissionId, int attempt, JudgeOutcome outcome) {
                calls.add("finish");
            }

            @Override
            public void retry(long submissionId, int attempt) {
                calls.add("retry");
                if (persistenceFailure) throw new IllegalStateException("Persistence unavailable");
            }
        };
        var sandbox = new ProtectedSandbox(request -> {
            calls.add("sandbox");
            if (temporaryFailure) throw new ResourceAccessException("Sandbox unavailable");
            var compilation = new ProcessResult();
            compilation.setExitCode(1);
            var response = new ExecuteCodeResponse();
            response.setCompileResult(compilation);
            return response;
        }, false);
        var channel = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(),
                new Class<?>[]{Channel.class}, (proxy, method, args) -> {
                    calls.add(method.getName());
                    assertEquals(7L, args[0]);
                    for (int i = 1; i < args.length; i++) assertEquals(false, args[i]);
                    return null;
                });
        new JudgeConsumer(problems, sandbox, new JudgeEvaluator(), true).consume(id, 2, 7, channel);
    }
}
