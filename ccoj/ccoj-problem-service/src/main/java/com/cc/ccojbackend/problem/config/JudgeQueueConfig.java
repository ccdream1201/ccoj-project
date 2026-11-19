package com.cc.ccojbackend.problem.config;

import com.cc.ccojbackend.model.submission.JudgeQueue;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JudgeQueueConfig {
    @Bean
    Queue judgeQueue() {
        return new Queue(JudgeQueue.NAME, true);
    }
    @Bean
    Queue judgeFailureQueue() {
        return new Queue(JudgeQueue.FAILURE_QUEUE, true);
    }
}
