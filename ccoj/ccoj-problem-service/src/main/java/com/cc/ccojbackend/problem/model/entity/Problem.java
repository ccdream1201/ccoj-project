package com.cc.ccojbackend.problem.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 题目表
 * @TableName problem
 */
@TableName(value ="problem")
@Data
public class Problem {
    /**
     * 题目ID
     */
    @TableId(type = IdType.AUTO)
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
    private String samples;

    /**
     * 判题用例，数组元素包含input和output，不向普通用户公开
     */
    private String judgeCases;

    /**
     * 判题限制：timeLimitMs单用例时间限制，memoryLimitMb内存限制
     */
    private String judgeConfig;

    /**
     * 官方题解，按权限公开
     */
    private String solution;

    /**
     * 发布状态：0草稿，1已发布，2已下架
     */
    private Integer status;

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

    /**
     * 逻辑删除：0未删除，1已删除
     */
    private Integer isDeleted;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}