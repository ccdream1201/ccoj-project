package com.cc.ccojbackend.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.model.dto.submission.SubmissionRequest;
import com.cc.ccojbackend.model.entity.Problem;
import com.cc.ccojbackend.model.entity.Submission;
import com.cc.ccojbackend.model.entity.judge.ProblemCase;
import com.cc.ccojbackend.model.vo.submission.SubmissionResponse;
import com.cc.ccojbackend.model.vo.user.UserVO;
import com.cc.ccojbackend.service.JudgeService;
import com.cc.ccojbackend.service.ProblemService;
import com.cc.ccojbackend.service.SubmissionService;
import com.cc.ccojbackend.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
* @author jiangchengchuan
* @description 针对表【submission(用户提交记录表)】的数据库操作Service实现
* @createDate 2026-11-01 15:54:01
*/
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission>
    implements SubmissionService{

    private final ProblemService problemService;
    private final JudgeService judgeService;
    private final JsonMapper jsonMapper;

    @Override
    public Long addSubmission(SubmissionRequest submissionRequest, UserVO currentUser) {

        // 1. 查询题目状态
        Long problemId = submissionRequest.getProblemId();

        Problem problem = problemService.getById(problemId);
        if (problem == null) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "题目不存在"
            );
        }

        if (!Integer.valueOf(1).equals(problem.getStatus())) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "题目未发布，不能提交"
            );
        }
        // 2. 题目正常，创建submission，返回id
        Submission newSubmission = new Submission();

        newSubmission.setProblemId(submissionRequest.getProblemId());
        newSubmission.setUserId(currentUser.getId());
        newSubmission.setLanguage(submissionRequest.getLanguage());
        newSubmission.setSourceCode(submissionRequest.getSourceCode());
        newSubmission.setJudgeInfo(null);
        newSubmission.setPassedCaseCount(0);

        List<ProblemCase> judgeCases = jsonMapper.readValue(
                problem.getJudgeCases(),
                new TypeReference<List<ProblemCase>>() {}
        );
        newSubmission.setTotalCaseCount(judgeCases.size());

        int rows = baseMapper.insert(newSubmission);
        if (rows != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        return newSubmission.getId();
    }

    @Override
    public SubmissionResponse addSubmissionAndJudge(SubmissionRequest submissionRequest, UserVO currentUser) {

        // 获取数据库中判题申请的id
        Long submissionId = addSubmission(submissionRequest, currentUser);
        // 进行判题操作
        return judgeService.judge(submissionId);
    }
}




