package com.cc.ccojbackend.client;

import com.cc.ccojbackend.model.sandbox.ExecuteCodeRequest;
import com.cc.ccojbackend.model.sandbox.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface SandboxClient {
    @PostExchange("/execute")
    ExecuteCodeResponse execute(@RequestBody ExecuteCodeRequest request);
}
