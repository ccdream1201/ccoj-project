package com.cc.ccojbackend.model.vo.submission;

import com.cc.ccojbackend.model.entity.judge.JudgeInfo;
import lombok.Data;

@Data
public class SubmissionResponse {
    /**
     * 提交ID
     */
    private Long id;

    /**
     * 处理状态：PENDING、RUNNING、SYSTEM_FAILED、ACCEPTED、WRONG_ANSWER、COMPILE_ERROR、RUNTIME_ERROR
     */
    private String status;


    /**
     * 判题配置，JSON对象包括最大耗时、最大内存占用
     */
    private JudgeInfo judgeInfo;

    /**
     * 通过用例数
     */
    private Integer passedCaseCount;

    /**
     * 本次判题用例总数
     */
    private Integer totalCaseCount;

    /**
     * 编译器输出，业务侧限制长度
     */
    private String compileOutput;
}
