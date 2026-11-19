package com.cc.ccojbackend.judge.config;

import com.cc.ccojbackend.client.HttpClients;
import com.cc.ccojbackend.client.ProblemJudgeClient;
import com.cc.ccojbackend.client.SandboxClient;
import com.cc.ccojbackend.model.submission.JudgeQueue;
import java.time.Duration;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class JudgeConfig {
    @Bean
    Queue judgeQueue() {
        return new Queue(JudgeQueue.NAME, true);
    }

    @Bean
    @LoadBalanced
    RestClient.Builder problemClientBuilder(@Value("${CCOJ_INTERNAL_KEY}") String key) {
        return HttpClients.internalBuilder(key);
    }

    @Bean
    ProblemJudgeClient problemJudgeClient(@Qualifier("problemClientBuilder") RestClient.Builder builder) {
        return HttpClients.bind(builder, "http://ccoj-problem-service", ProblemJudgeClient.class);
    }

    @Bean
    SandboxClient sandboxClient(@Value("${sandbox.base-url}") String url) {
        return HttpClients.bind(HttpClients.builder(Duration.ofSeconds(120)), url, SandboxClient.class);
    }
}
