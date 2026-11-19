package com.cc.ccojbackend.judge.service;

import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.cc.ccojbackend.client.SandboxClient;
import com.cc.ccojbackend.model.sandbox.ExecuteCodeRequest;
import com.cc.ccojbackend.model.sandbox.ExecuteCodeResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ProtectedSandbox {
    private static final String RESOURCE = "ccoj.sandbox.execute";
    private final SandboxClient sandbox;
    private final boolean circuitEnabled;

    public ProtectedSandbox(SandboxClient sandbox,
                            @Value("${ccoj.protection.sandbox-circuit-enabled:true}") boolean circuitEnabled) {
        this.sandbox = sandbox;
        this.circuitEnabled = circuitEnabled;
        if (circuitEnabled) {
            var rule = new DegradeRule(RESOURCE)
                    .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                    .setCount(0.5)
                    .setMinRequestAmount(5)
                    .setStatIntervalMs(30_000)
                    .setTimeWindow(10);
            DegradeRuleManager.loadRules(List.of(rule));
        }
    }

    public ExecuteCodeResponse execute(ExecuteCodeRequest request) {
        if (!circuitEnabled) return invoke(request);
        try (var entry = SphU.entry(RESOURCE)) {
            try {
                return invoke(request);
            } catch (TemporaryFailure failure) {
                Tracer.traceEntry(failure, entry);
                throw failure;
            }
        } catch (BlockException blocked) {
            throw new TemporaryFailure("Sandbox circuit is open", blocked);
        }
    }

    private ExecuteCodeResponse invoke(ExecuteCodeRequest request) {
        try {
            return sandbox.execute(request);
        } catch (ResourceAccessException failure) {
            throw new TemporaryFailure("Sandbox connection failed or timed out", failure);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status >= 500 && status <= 599) {
                throw new TemporaryFailure("Sandbox returned HTTP " + status, failure);
            }
            throw failure;
        }
    }

    public static final class TemporaryFailure extends RuntimeException {
        private TemporaryFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
