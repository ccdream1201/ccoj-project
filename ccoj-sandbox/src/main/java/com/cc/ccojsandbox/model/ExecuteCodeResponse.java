package com.cc.ccojsandbox.model;

import lombok.Data;

import java.util.List;

@Data
public class ExecuteCodeResponse {

    /**
     * 编译结果
     */
    private ProcessResult compileResult;

    /**
     * 运行结果
     */
    private List<ProcessResult> runResult;
}
