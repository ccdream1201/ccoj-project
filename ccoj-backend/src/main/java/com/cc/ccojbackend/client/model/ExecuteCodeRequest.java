package com.cc.ccojbackend.client.model;

import lombok.Data;

import java.util.List;

@Data
public class ExecuteCodeRequest {

    private String sourceCode;

    private List<String> inputList;

    private Long timeLimitMs;

    private Long memoryLimitMb;
}
