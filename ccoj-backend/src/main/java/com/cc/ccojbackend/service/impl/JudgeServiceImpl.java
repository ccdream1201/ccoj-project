package com.cc.ccojbackend.service.impl;

import com.cc.ccojbackend.client.SandboxClient;
import com.cc.ccojbackend.client.model.ExecuteCodeRequest;
import com.cc.ccojbackend.client.model.ExecuteCodeResponse;
import com.cc.ccojbackend.client.model.ProcessResult;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.mapper.SubmissionMapper;
import com.cc.ccojbackend.model.entity.Problem;
import com.cc.ccojbackend.model.entity.Submission;
import com.cc.ccojbackend.model.entity.judge.JudgeConfig;
import com.cc.ccojbackend.model.entity.judge.ProblemCase;
import com.cc.ccojbackend.model.enums.SubmissionStatus;
import com.cc.ccojbackend.model.vo.submission.SubmissionResponse;
import com.cc.ccojbackend.service.JudgeService;
import com.cc.ccojbackend.service.ProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {

    final SandboxClient sandboxClient;
    final SubmissionMapper submissionMapper;
    final ProblemService problemService;
    private final JsonMapper jsonMapper;

    /**
     * 判题操作
     *
     * @param submissionId 判题记录的id
     * @return 返回判题结果
     */
    @Override
    public SubmissionResponse judge(Long submissionId) {

        Submission submission = submissionMapper.selectById(submissionId);

        if (submission == null) {
            throw new BusinessException(
                    ResultCode.PARAMS_ERROR.getCode(),
                    "提交记录不存在"
            );
        }
        int claimed = submissionMapper.update(null, Wrappers.<Submission>lambdaUpdate()
                .eq(Submission::getId, submissionId)
                .eq(Submission::getStatus, SubmissionStatus.PENDING.getValue())
                .set(Submission::getStatus, SubmissionStatus.RUNNING.getValue()));
        if (claimed != 1) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "提交已开始判题");
        }

        SubmissionResponse result;
        try {
            Problem problem = problemService.getById(submission.getProblemId());
            if (problem == null) {
                throw new IllegalStateException("判题题目不存在");
            }
            List<ProblemCase> cases = jsonMapper.readValue(problem.getJudgeCases(),
                    new TypeReference<List<ProblemCase>>() {});
            JudgeConfig config = jsonMapper.readValue(problem.getJudgeConfig(), JudgeConfig.class);
            if (cases == null || cases.isEmpty() || config == null) {
                throw new IllegalStateException("题目判题配置不完整");
            }

            ExecuteCodeRequest request = getExecuteCodeRequest(cases, submission);
            request.setTimeLimitMs(config.getTimeLimit());
            request.setMemoryLimitMb(config.getMemoryLimit());
            result = evaluate(sandboxClient.execute(request), cases);
        } catch (RuntimeException e) {
            log.error("提交 {} 判题失败", submissionId, e);
            result = new SubmissionResponse();
            result.setStatus(SubmissionStatus.SYSTEM_FAILED.getValue());
            result.setPassedCaseCount(0);
            result.setTotalCaseCount(submission.getTotalCaseCount());
        }

        result.setId(submissionId);
        saveJudgeResult(result);
        return result;
    }

    private static ExecuteCodeRequest getExecuteCodeRequest(List<ProblemCase> judgeCases, Submission submission) {
        List<String> inputs = new ArrayList<>();

        for (ProblemCase judgeCase : judgeCases) {
            if (judgeCase == null
                    || judgeCase.getInput() == null
                    || judgeCase.getOutput() == null) {
                throw new BusinessException(
                        ResultCode.SYSTEM_ERROR.getCode(),
                        "题目判题用例配置错误"
                );
            }

            inputs.add(judgeCase.getInput());
        }
        // 将需要的东西送到代码沙箱
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setSourceCode(submission.getSourceCode());
        executeCodeRequest.setInputList(inputs);
        return executeCodeRequest;
    }

    private SubmissionResponse evaluate(ExecuteCodeResponse response, List<ProblemCase> cases) {
        SubmissionResponse result = new SubmissionResponse();
        result.setPassedCaseCount(0);
        result.setTotalCaseCount(cases.size());

        ProcessResult compile = response.getCompileResult();
        SubmissionStatus compileLimit = executionLimit(compile);
        if (compileLimit != null) {
            result.setStatus(compileLimit.getValue());
            return result;
        }
        if (compile.getExitCode() != 0) {
            result.setStatus(SubmissionStatus.COMPILE_ERROR.getValue());
            String output = compile.getStderr();
            if (output != null) {
                result.setCompileOutput(output.substring(0, Math.min(4096, output.length())));
            }
            return result;
        }

        List<ProcessResult> runs = response.getRunResult();
        if (runs == null || runs.size() != cases.size()) {
            throw new IllegalStateException("沙箱返回的运行结果数量不匹配");
        }

        int passed = 0;
        SubmissionStatus firstFailure = null;
        for (int i = 0; i < runs.size(); i++) {
            ProcessResult run = runs.get(i);
            SubmissionStatus verdict = executionLimit(run);
            if (verdict == null && run.getExitCode() != 0) {
                verdict = SubmissionStatus.RUNTIME_ERROR;
            }
            if (verdict == null && !normalizeOutput(cases.get(i).getOutput()).equals(normalizeOutput(run.getStdout()))) {
                verdict = SubmissionStatus.WRONG_ANSWER;
            }
            if (verdict == null) {
                passed++;
            } else if (firstFailure == null) {
                firstFailure = verdict;
            }
        }
        result.setPassedCaseCount(passed);
        result.setStatus((firstFailure == null ? SubmissionStatus.ACCEPTED : firstFailure).getValue());
        return result;
    }

    private SubmissionStatus executionLimit(ProcessResult process) {
        if (process == null || process.getExitCode() == null) {
            throw new IllegalStateException("沙箱返回的进程结果不完整");
        }
        String limit = process.getLimitExceeded();
        if (limit == null) {
            return null;
        }
        return switch (limit) {
            case "TIME" -> SubmissionStatus.TIME_LIMIT;
            case "MEMORY" -> SubmissionStatus.MEMORY_LIMIT;
            case "OUTPUT" -> SubmissionStatus.OUTPUT_LIMIT;
            default -> throw new IllegalStateException("无法识别的沙箱限制类型");
        };
    }

    private String normalizeOutput(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }

    private void saveJudgeResult(SubmissionResponse result) {

        Submission update = new Submission();
        update.setId(result.getId());
        update.setStatus(result.getStatus());
        update.setPassedCaseCount(result.getPassedCaseCount());
        update.setTotalCaseCount(result.getTotalCaseCount());
        update.setCompileOutput(result.getCompileOutput());

        if (result.getJudgeInfo() != null) {
            update.setJudgeInfo(
                    jsonMapper.writeValueAsString(result.getJudgeInfo())
            );
        }

        if (submissionMapper.updateById(update) != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }
    }
}
