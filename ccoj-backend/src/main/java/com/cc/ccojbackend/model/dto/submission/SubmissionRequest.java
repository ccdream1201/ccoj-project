package com.cc.ccojbackend.model.dto.submission;

import jakarta.validation.constraints.*;
import lombok.Data;


@Data
public class SubmissionRequest {

    /**
     * 题目的id
     */
    @NotNull
    @Positive
    private Long problemId;

    /**
     * 源代码
     */
    @NotBlank
    @Size(max = 65536)
    private String sourceCode;

    /**
     * 语言
     */
    @NotBlank
    @Pattern(regexp = "JAVA", message = "目前仅支持 JAVA")
    private String language;
}
