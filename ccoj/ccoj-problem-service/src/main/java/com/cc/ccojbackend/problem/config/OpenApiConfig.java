package com.cc.ccojbackend.problem.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI serviceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("CCOJ 题目与提交服务")
                        .description("受保护的接口使用当前浏览器的 CCOJ_SESSION。先在用户服务文档中执行登录。"))
                .addServersItem(new Server().url("/").description("当前网关"));
    }
}
