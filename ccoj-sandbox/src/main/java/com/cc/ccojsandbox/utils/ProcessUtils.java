package com.cc.ccojsandbox.utils;

import com.cc.ccojsandbox.model.ProcessResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 进程工具类
 */
@Slf4j
public class ProcessUtils {


    private ProcessUtils() {
    }

    /**
     * 运行进程，并且返回结果
     *
     * @param inputFile
     * @param message
     * @return
     */
    public static ProcessResult run(ProcessBuilder processBuilder, Path inputFile,
                                    Path stdoutFile, Path stderrFile, String message) throws IOException, InterruptedException {

        // 1. 配置标准输入
        if (inputFile != null) {
            processBuilder.redirectInput(inputFile.toFile());
        } else {
            processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
        }

        // 2. 分别保存标准输出和错误输出
        processBuilder.redirectErrorStream(false);
        processBuilder.redirectOutput(stdoutFile.toFile());
        processBuilder.redirectError(stderrFile.toFile());

        // 3. 启动进程
        Process process = processBuilder.start();

        try {
            // 无输入时发送 EOF，避免程序等待控制台输入
            if (inputFile == null) {
                process.getOutputStream().close();
            }

            // 4. 等待进程结束
            int exitCode = process.waitFor();

            // 5. 读取输出文件
            String stdout = Files.readString(
                    stdoutFile,
                    StandardCharsets.UTF_8
            );

            String stderr = Files.readString(
                    stderrFile,
                    StandardCharsets.UTF_8
            );

            // 6. 整理结果
            ProcessResult result = new ProcessResult();
            result.setExitCode(exitCode);
            result.setStdout(stdout);
            result.setStderr(stderr);

            log.info("{}结束，退出码：{}", message, exitCode);

            return result;

        } finally {
            // 等待被中断或发生异常时，尝试终止仍在运行的进程
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

}
