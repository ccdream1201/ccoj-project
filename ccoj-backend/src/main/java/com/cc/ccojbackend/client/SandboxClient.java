package com.cc.ccojbackend.client;

import com.cc.ccojbackend.client.model.ExecuteCodeRequest;
import com.cc.ccojbackend.client.model.ExecuteCodeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SandboxClient {

    private final RestClient restClient;

    public SandboxClient(@Value("${sandbox.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofMinutes(2));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    public ExecuteCodeResponse execute(
            ExecuteCodeRequest request) {

        ExecuteCodeResponse response = restClient.post()
                .uri("/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ExecuteCodeResponse.class);

        if (response == null) {
            throw new IllegalStateException("沙箱返回空响应");
        }

        return response;
    }
}
