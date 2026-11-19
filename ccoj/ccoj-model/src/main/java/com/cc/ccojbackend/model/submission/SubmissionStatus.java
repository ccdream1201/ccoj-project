package com.cc.ccojbackend.model.submission;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubmissionStatus {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    ACCEPTED("ACCEPTED"),
    WRONG_ANSWER("WRONG_ANSWER"),
    COMPILE_ERROR("COMPILE_ERROR"),
    RUNTIME_ERROR("RUNTIME_ERROR"),
    TIME_LIMIT("TIME_LIMIT"),
    MEMORY_LIMIT("MEMORY_LIMIT"),
    OUTPUT_LIMIT("OUTPUT_LIMIT"),
    SYSTEM_FAILED("SYSTEM_FAILED");

    private final String value;
}
