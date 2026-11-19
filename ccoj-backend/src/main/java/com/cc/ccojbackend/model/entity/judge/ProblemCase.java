package com.cc.ccojbackend.model.entity.judge;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProblemCase {

    /**
     * 输入
     */
    @NotNull
    private String input;

    /**
     * 输出
     */
    @NotNull
    private String output;
}
