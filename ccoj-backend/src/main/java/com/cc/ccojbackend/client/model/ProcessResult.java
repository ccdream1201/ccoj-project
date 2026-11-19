package com.cc.ccojbackend.client.model;

import lombok.Data;

@Data
public class ProcessResult {


    /**
     * 进程退出码
     */
    private Integer exitCode;

    /**
     * 输出
     */
    private String stdout;

    /**
     * 错误输出
     */
    private String stderr;

    private String limitExceeded;
}
