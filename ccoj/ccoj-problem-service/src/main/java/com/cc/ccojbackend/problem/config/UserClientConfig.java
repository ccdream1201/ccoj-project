package com.cc.ccojbackend.problem.config;

import com.cc.ccojbackend.client.HttpClients;
import com.cc.ccojbackend.client.UserDirectoryClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class UserClientConfig {
    @Bean
    @LoadBalanced
    RestClient.Builder userDirectoryBuilder(@Value("${CCOJ_INTERNAL_KEY}") String key) {
        return HttpClients.internalBuilder(key);
    }

    @Bean
    UserDirectoryClient userDirectoryClient(@Qualifier("userDirectoryBuilder") RestClient.Builder builder) {
        return HttpClients.bind(builder, "http://ccoj-user-service", UserDirectoryClient.class);
    }
}
