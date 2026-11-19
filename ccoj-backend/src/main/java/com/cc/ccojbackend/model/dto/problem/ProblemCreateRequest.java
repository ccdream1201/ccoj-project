package com.cc.ccojbackend.model.dto.problem;

import com.cc.ccojbackend.model.entity.judge.JudgeConfig;
import com.cc.ccojbackend.model.entity.judge.ProblemCase;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class ProblemCreateRequest {


    /**
     * 题目标题
     */
    @NotBlank
    @Size(max = 200)
    private String title;

    /**
     * 题目描述
     */
    @NotBlank
    private String description;

    /**
     * 难度：1简单，2中等，3困难
     */
    @NotNull
    @Min(1)
    @Max(3)
    private Integer difficulty;

    /**
     * 公开样例，数组元素包含input和output
     */
    @NotNull
    private List<@NotNull @Valid ProblemCase> samples;

    /**
     * 判题用例，数组元素包含input和output，不向普通用户公开
     */
    @NotEmpty
    @Size(max = 64)
    private List<@NotNull @Valid ProblemCase> judgeCases;

    /**
     * 判题限制：timeLimitMs单用例时间限制，memoryLimitMb内存限制
     */
    @NotNull
    @Valid
    private JudgeConfig judgeConfig;

    /**
     * 官方题解，按权限公开
     */
    private String solution;
}
