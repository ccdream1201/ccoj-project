package com.cc.ccojbackend.problem.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

import lombok.Data;

/**
 * 用户提交记录表
 * @TableName submission
 */
@TableName(value ="submission")
@Data
public class Submission {
    /**
     * 提交ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 题目ID
     */
    private Long problemId;

    /**
     * 提交者用户ID
     */
    private Long userId;

    /**
     * 语言标识，例如JAVA、CPP、PYTHON
     */
    private String language;

    /**
     * 提交的源代码
     */
    private String sourceCode;

    /**
     * 处理状态：PENDING、RUNNING、FINISHED、SYSTEM_FAILED
     */
    private String status;

    private Integer judgeAttempt;

    private Date retryAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String pendingFailureReason;

    /**
     * 判题配置，JSON对象包括最大耗时、最大内存占用
     */
    private String judgeInfo;

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

    /**
     * 逻辑删除：0未删除，1已删除
     */
    private Integer isDeleted;

    /**
     * 提交创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
