package com.cc.ccojsandbox.controller;

import com.cc.ccojsandbox.model.ExecuteCodeRequest;
import com.cc.ccojsandbox.model.ExecuteCodeResponse;
import com.cc.ccojsandbox.sandbox.CodeSandbox;
import com.cc.ccojsandbox.sandbox.JavaDockerCodeSandbox;
import com.cc.ccojsandbox.sandbox.JavaNativeCodeSandbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/execute")
public class SandboxController {

    private final CodeSandbox sandbox;

    public SandboxController(@Value("${sandbox.mode}") String mode,
                             @Value("${sandbox.docker.executable}") String dockerExecutable,
                             @Value("${sandbox.docker.image}") String dockerImage) {
        sandbox = switch (mode) {
            case "docker" -> new JavaDockerCodeSandbox(dockerExecutable, dockerImage);
            case "native" -> new JavaNativeCodeSandbox();
            default -> throw new IllegalArgumentException("Unsupported sandbox mode: " + mode);
        };
    }

    @PostMapping
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest request) {
        try {
            return sandbox.execute(request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
