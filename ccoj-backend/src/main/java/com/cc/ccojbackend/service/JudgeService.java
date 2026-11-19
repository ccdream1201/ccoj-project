package com.cc.ccojbackend.service;

import com.cc.ccojbackend.model.vo.submission.SubmissionResponse;

public interface JudgeService {

    SubmissionResponse judge(Long submissionId);
}
