package com.cc.ccojbackend.model.submission;

public final class JudgeQueue {
    public static final String NAME = "ccoj.judge";

    public static final String FAILURE_QUEUE = "ccoj.judge.failed";
    public static final String ATTEMPT_HEADER = "x-ccoj-judge-attempt";

    private JudgeQueue() {}
}
