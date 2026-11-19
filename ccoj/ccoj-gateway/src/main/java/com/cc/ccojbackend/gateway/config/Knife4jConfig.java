package com.cc.ccojbackend.gateway.config;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ccoj.api-docs.enabled", havingValue = "true")
class Knife4jConfig {

    @Bean
    RouterFunction<ServerResponse> knife4jRoutes() {
        return RouterFunctions.route()
                .resource(RequestPredicates.GET("/doc.html"),
                        new ClassPathResource("META-INF/resources/doc.html"))
                .resources("/webjars/**", new ClassPathResource("META-INF/resources/webjars/"))
                .GET("/v3/api-docs/swagger-config", request -> ServerResponse.ok().bodyValue(
                        new DocumentConfiguration(List.of(
                                new Document("用户服务", "/user/docs/openapi"),
                                new Document("题目与提交服务", "/problem/docs/openapi")
                        ))))
                .build();
    }

    private record DocumentConfiguration(List<Document> urls) {}

    private record Document(String name, String url) {}
}
