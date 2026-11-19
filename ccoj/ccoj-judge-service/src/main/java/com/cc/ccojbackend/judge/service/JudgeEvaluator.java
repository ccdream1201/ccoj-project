package com.cc.ccojbackend.judge.service;

import com.cc.ccojbackend.model.sandbox.ExecuteCodeResponse;
import com.cc.ccojbackend.model.submission.JudgeOutcome;
import com.cc.ccojbackend.model.submission.JudgeTask;
import com.cc.ccojbackend.model.submission.SubmissionStatus;
import org.springframework.stereotype.Component;

@Component
public class JudgeEvaluator {
    public JudgeOutcome evaluate(JudgeTask task, ExecuteCodeResponse response) {
        int total = task.cases().size();
        if (response == null || response.getCompileResult() == null
                || response.getCompileResult().getExitCode() == null) {
            return failed(total);
        }
        var compile = response.getCompileResult();
        if (compile.getLimitExceeded() != null) {
            return limited(compile.getLimitExceeded(), 0, total);
        }
        if (compile.getExitCode() != 0) {
            String diagnostic = compile.getStderr();
            if (diagnostic != null && diagnostic.length() > 12000) {
                diagnostic = diagnostic.substring(0, 12000);
            }
            return new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, total, diagnostic);
        }
        var results = response.getRunResult();
        if (results == null || results.size() > total) return failed(total);

        int passed = 0;
        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            if (result == null || result.getExitCode() == null) return failed(total);
            if (result.getLimitExceeded() != null) {
                return limited(result.getLimitExceeded(), passed, total);
            }
            if (result.getExitCode() != 0) {
                return new JudgeOutcome(SubmissionStatus.RUNTIME_ERROR, passed, total, null);
            }
            if (result.getStdout() == null) return failed(total);
            String expected = task.cases().get(i).getOutput();
            if (normalize(expected).equals(normalize(result.getStdout()))) passed++;
        }
        if (results.size() != total) return failed(total);
        return new JudgeOutcome(passed == total ? SubmissionStatus.ACCEPTED : SubmissionStatus.WRONG_ANSWER,
                passed, total, null);
    }

    private JudgeOutcome limited(String limit, int passed, int total) {
        var status = switch (limit) {
            case "TIME" -> SubmissionStatus.TIME_LIMIT;
            case "MEMORY" -> SubmissionStatus.MEMORY_LIMIT;
            case "OUTPUT" -> SubmissionStatus.OUTPUT_LIMIT;
            default -> SubmissionStatus.SYSTEM_FAILED;
        };
        return new JudgeOutcome(status, passed, total, null);
    }

    public JudgeOutcome failed(int total) {
        return new JudgeOutcome(SubmissionStatus.SYSTEM_FAILED, 0, total, null);
    }

    private static String normalize(String output) {
        return output.replace("\r\n", "\n").stripTrailing();
    }
}
