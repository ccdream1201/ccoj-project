package com.cc.ccojbackend.service;

import com.cc.ccojbackend.model.dto.submission.SubmissionRequest;
import com.cc.ccojbackend.model.entity.Submission;
import com.baomidou.mybatisplus.spring.service.IService;
import com.cc.ccojbackend.model.vo.submission.SubmissionResponse;
import com.cc.ccojbackend.model.vo.user.UserVO;

/**
* @author jiangchengchuan
* @description 针对表【submission(用户提交记录表)】的数据库操作Service
* @createDate 2026-11-01 15:54:01
*/
public interface SubmissionService extends IService<Submission> {

    Long addSubmission(SubmissionRequest submissionRequest,
                       UserVO currentUser);

    SubmissionResponse addSubmissionAndJudge(SubmissionRequest submissionRequest, UserVO currentUser);
}
