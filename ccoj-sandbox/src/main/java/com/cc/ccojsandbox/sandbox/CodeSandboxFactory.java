package com.cc.ccojsandbox.sandbox;

public class CodeSandboxFactory {

    private CodeSandboxFactory() {
    }

    public static CodeSandbox getCodeSandbox(String type) {
        return switch (type) {
            case "native" -> new JavaNativeCodeSandbox();
            case "cloud" -> new JavaCloudCodeSandbox();
            case "docker" -> new JavaDockerCodeSandbox();
            default -> throw new IllegalArgumentException(
                    "不支持的沙箱类型：" + type);
        };
    }
}
