package com.cc.ccojbackend.controller;

import com.cc.ccojbackend.common.exception.BusinessException;
import com.cc.ccojbackend.common.result.ResultCode;
import com.cc.ccojbackend.model.entity.Submission;
import com.cc.ccojbackend.model.entity.judge.JudgeInfo;
import com.cc.ccojbackend.model.enums.UserRole;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tools.jackson.databind.json.JsonMapper;

import com.cc.ccojbackend.auth.LoginUserProvider;
import com.cc.ccojbackend.common.result.Result;
import com.cc.ccojbackend.model.dto.submission.SubmissionRequest;
import com.cc.ccojbackend.model.vo.submission.SubmissionResponse;
import com.cc.ccojbackend.model.vo.user.UserVO;
import com.cc.ccojbackend.service.SubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接受代码提交，查询提交结果
 */
@RestController
@RequestMapping("/submission")
@RequiredArgsConstructor
public class SubmissionController {

    private final LoginUserProvider loginUserProvider;
    private final SubmissionService submissionService;
    private final JsonMapper jsonMapper;

    @PostMapping("/judge")
    public Result<SubmissionResponse> judgeProblem(@Valid @RequestBody SubmissionRequest submissionRequest,
                                                   HttpServletRequest request) {


        // 获取当前申请判题的用户
        UserVO currentUser = loginUserProvider.getCurrentUser(request);

        // 判题并且返回结果
        SubmissionResponse submissionResponse = submissionService.addSubmissionAndJudge(submissionRequest, currentUser);

        return Result.success(submissionResponse);
    }

    @GetMapping("/{id}")
    public Result<SubmissionResponse> getSubmission(@PathVariable Long id, HttpServletRequest request) {
        UserVO user = loginUserProvider.getCurrentUser(request);
        Submission submission = submissionService.getById(id);
        if (submission == null || (!user.getId().equals(submission.getUserId())
                && !UserRole.ADMIN.getValue().equals(user.getUserRole()))) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        SubmissionResponse response = new SubmissionResponse();
        BeanUtils.copyProperties(submission, response, "judgeInfo");
        if (submission.getJudgeInfo() != null) {
            response.setJudgeInfo(jsonMapper.readValue(submission.getJudgeInfo(), JudgeInfo.class));
        }
        return Result.success(response);
    }
}
