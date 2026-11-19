package com.cc.ccojbackend.model.vo.problem;

import com.cc.ccojbackend.model.entity.judge.JudgeConfig;
import com.cc.ccojbackend.model.entity.judge.ProblemCase;
import lombok.Data;

import java.util.List;

@Data
public class ProblemVO {

    /**
     * 题目ID
     */
    private Long id;

    /**
     * 题目标题
     */
    private String title;

    /**
     * 题目描述
     */
    private String description;

    /**
     * 难度：1简单，2中等，3困难
     */
    private Integer difficulty;

    /**
     * 公开样例，数组元素包含input和output
     */
    private List<ProblemCase> samples;

    /**
     * 判题限制：timeLimitMs单用例时间限制，memoryLimitMb内存限制
     */
    private JudgeConfig judgeConfig;

    /**
     * 官方题解，按权限公开
     */
    private String solution;

    /**
     * 点赞人数
     */
    private Long likeCount;

    /**
     * 收藏人数
     */
    private Long favoriteCount;

    /**
     * 提交次数
     */
    private Long submissionCount;

    /**
     * 通过的提交次数
     */
    private Long acceptedCount;

    /**
     * 创建者用户ID
     */
    private Long createdBy;

}
