package com.cc.ccojbackend.model.sandbox;

import lombok.Data;

import java.util.List;

@Data
public class ExecuteCodeRequest {

    private String sourceCode;

    private List<String> inputList;

    private Long timeLimitMs;

    private Long memoryLimitMb;
}
