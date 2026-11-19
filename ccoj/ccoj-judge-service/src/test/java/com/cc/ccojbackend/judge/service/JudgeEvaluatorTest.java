package com.cc.ccojbackend.judge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cc.ccojbackend.model.problem.ProblemCase;
import com.cc.ccojbackend.model.sandbox.ExecuteCodeResponse;
import com.cc.ccojbackend.model.sandbox.ProcessResult;
import com.cc.ccojbackend.model.submission.JudgeOutcome;
import com.cc.ccojbackend.model.submission.JudgeTask;
import com.cc.ccojbackend.model.submission.SubmissionStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JudgeEvaluatorTest {
    private final JudgeEvaluator evaluator = new JudgeEvaluator();
    private final JudgeTask task = new JudgeTask("", List.of(testCase("3\n"), testCase("5\n")), 2000L, 256L);

    @Test
    void acceptsCrLfAndTrailingWhitespace() {
        assertEquals(new JudgeOutcome(SubmissionStatus.ACCEPTED, 2, 2, null),
                evaluate(result(0, "3\r\n", null), result(0, "5  \r\n", null)));
    }

    @Test
    void countsPassingCasesOnWrongAnswer() {
        assertEquals(new JudgeOutcome(SubmissionStatus.WRONG_ANSWER, 1, 2, null),
                evaluate(result(0, "4", null), result(0, "5", null)));
    }

    @Test
    void limitsCompileDiagnosticsAndSkipsRunResults() {
        var compilation = result(1, "", null);
        compilation.setStderr("x".repeat(13_000));
        var response = new ExecuteCodeResponse();
        response.setCompileResult(compilation);
        assertEquals(new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, 2, "x".repeat(12_000)),
                evaluator.evaluate(task, response));
    }

    @ParameterizedTest
    @CsvSource({"TIME,TIME_LIMIT", "MEMORY,MEMORY_LIMIT", "OUTPUT,OUTPUT_LIMIT", "UNKNOWN,SYSTEM_FAILED"})
    void mapsResourceLimitsAndRetainsEarlierPasses(String limit, SubmissionStatus status) {
        assertEquals(new JudgeOutcome(status, 1, 2, null),
                evaluate(result(0, "3", null), result(1, "", limit)));
    }

    @Test
    void mapsUnsuccessfulExecutionWithNoLimitToRuntimeError() {
        assertEquals(new JudgeOutcome(SubmissionStatus.RUNTIME_ERROR, 1, 2, null),
                evaluate(result(0, "3", null), result(1, "", null)));
    }

    @Test
    void truncatedOrMissingSandboxResponseBecomesSystemFailure() {
        var expected = new JudgeOutcome(SubmissionStatus.SYSTEM_FAILED, 0, 2, null);
        assertEquals(expected, evaluate(result(0, "3", null)));
        assertEquals(expected, evaluator.evaluate(task, null));
    }

    private JudgeOutcome evaluate(ProcessResult... runs) {
        var response = new ExecuteCodeResponse();
        response.setCompileResult(result(0, "", null));
        response.setRunResult(List.of(runs));
        return evaluator.evaluate(task, response);
    }

    private static ProcessResult result(int exitCode, String output, String limit) {
        var result = new ProcessResult();
        result.setExitCode(exitCode);
        result.setStdout(output);
        result.setLimitExceeded(limit);
        return result;
    }

    private static ProblemCase testCase(String expected) {
        var test = new ProblemCase();
        test.setInput("");
        test.setOutput(expected);
        return test;
    }
}
