package com.cc.ccojbackend.model.entity.judge;

import lombok.Data;

@Data
public class JudgeInfo {

    /**
     * 最大时间消耗
     */
    private Long maxTime;

    /**
     * 最大内存消耗
     */
    private Long maxMemory;
}
