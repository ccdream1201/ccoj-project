package com.cc.ccojbackend.model.submission;

public record JudgeOutcome(SubmissionStatus status, int passedCaseCount, int totalCaseCount,
                           String compileOutput) {}
