package com.cc.ccojbackend.judge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.cc.ccojbackend.model.sandbox.ExecuteCodeRequest;
import com.cc.ccojbackend.model.sandbox.ExecuteCodeResponse;
import com.cc.ccojbackend.model.sandbox.ProcessResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class ProtectedSandboxTest {

    @AfterEach
    void clearRules() {
        DegradeRuleManager.loadRules(List.of());
    }

    @Test
    void compileErrorsRemainNormalResponsesAndTransportFailuresOpenCircuit() {
        var calls = new AtomicInteger();
        var response = new ExecuteCodeResponse();
        var compile = new ProcessResult();
        compile.setExitCode(1);
        response.setCompileResult(compile);

        var sandbox = new ProtectedSandbox(request -> {
            if (calls.incrementAndGet() <= 6) return response;
            throw new ResourceAccessException("connection failed");
        }, true);
        var request = new ExecuteCodeRequest();
        for (int i = 0; i < 6; i++) assertSame(response, sandbox.execute(request));

        for (int i = 0; i < 7; i++) {
            assertThrows(ProtectedSandbox.TemporaryFailure.class, () -> sandbox.execute(request));
        }
        int callsBeforeBlockedAttempt = calls.get();
        assertThrows(ProtectedSandbox.TemporaryFailure.class, () -> sandbox.execute(request));
        assertEquals(callsBeforeBlockedAttempt, calls.get());
    }
}
