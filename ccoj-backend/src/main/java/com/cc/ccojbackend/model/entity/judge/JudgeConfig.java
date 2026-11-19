package com.cc.ccojbackend.model.entity.judge;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class JudgeConfig {

    /**
     * 时间限制
     */
    @NotNull
    @Min(100)
    @Max(5000)
    private Long timeLimit;

    /**
     * 空间限制
     */
    @NotNull
    @Min(64)
    @Max(512)
    private Long memoryLimit;

    /**
     * 堆栈限制
     */
    private Long stackLimit;
}
