package com.cc.ccojsandbox.sandbox;

import com.cc.ccojsandbox.model.ExecuteCodeRequest;
import com.cc.ccojsandbox.model.ExecuteCodeResponse;
import com.cc.ccojsandbox.model.ProcessResult;
import com.cc.ccojsandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Java原生沙箱
 */
@Slf4j
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {

    /**
     * 第一版约定提交代码的入口类为 Main，且不声明 package
     */
    private static final String SOURCE_FILE_NAME = "Main.java";

    @Override
    public ExecuteCodeResponse execute(ExecuteCodeRequest request) {

        String sourceCode = request.getSourceCode();
        List<String> inputList = request.getInputList();
        ExecuteCodeResponse response = new ExecuteCodeResponse();

        Path workDir = null;

        try {
            workDir = Files.createTempDirectory("ccoj-sandbox-");
            // 1. 保存代码到文件
            saveCodeToFile(workDir, sourceCode);
            // 2. 编译
            ProcessResult compileResult = compileCode(workDir);
            response.setCompileResult(compileResult);
            // 编译失败直接退出，不再运行
            if (!Integer.valueOf(0).equals(compileResult.getExitCode())) {
                return response;
            }
            // 3. 运行
            List<ProcessResult> runResult = runCode(workDir, inputList);
            response.setRunResult(runResult);

            return response;
        } catch (IOException e) {
            throw new UncheckedIOException("保存源代码失败", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 5. 无论成功、编译失败，还是抛出异常，都尝试清理
            if (workDir != null) {
                try {
                    deleteWorkDir(workDir);
                    log.info("工作目录清理完成：{}", workDir);
                } catch (IOException e) {
                    log.error("工作目录清理失败：{}", workDir, e);
                }
            }
        }
    }

    /**
     * 将代码写入文件，不创建文件夹
     *
     * @param sourceCode
     * @return
     * @throws IOException
     */
    public void saveCodeToFile(Path workDir, String sourceCode) throws IOException {

        Path sourceFile = workDir.resolve(SOURCE_FILE_NAME);

        Files.writeString(
                sourceFile,
                sourceCode,
                StandardCharsets.UTF_8
        );
        log.info("源文件保存成功：{}", sourceFile);
    }

    /**
     * 执行编译，返回编译信息
     *
     * @param workDir
     * @return
     */
    public ProcessResult compileCode(Path workDir)
            throws IOException, InterruptedException {
        // 为了确保传入相对路径也可以使用
        workDir = workDir.toAbsolutePath().normalize();

        // 获取当前环境中的javac
        Path javacPath = Path.of(System.getProperty("java.home"), "bin", "javac");
        // 配置编译进程
        ProcessBuilder compileProcessBuilder = new ProcessBuilder(
                javacPath.toString(),
                "-encoding", "UTF-8",
                workDir.resolve("Main.java").toString()
        );
        // 设置工作目录
        compileProcessBuilder.directory(workDir.toFile());

        // 运行编译进程得到结果
        ProcessResult compileResult = ProcessUtils.run(compileProcessBuilder,
                null, workDir.resolve("compile.stdout"),
                workDir.resolve("compile.stderr"), "编译");

        return compileResult;
    }

    /**
     * 执行程序，返回运行信息
     *
     * @param workDir
     * @param inputList 输入
     * @return
     */
    public List<ProcessResult> runCode(Path workDir, List<String> inputList)
            throws IOException, InterruptedException {
        workDir = workDir.toAbsolutePath().normalize();

        List<ProcessResult> processResults = new ArrayList<>();

        // 获取当前环境中的java
        Path javaPath = Path.of(System.getProperty("java.home"), "bin", "java");

        for (int i = 0; i < inputList.size(); i++) {

            Path inputFile = workDir.resolve("case-" + i + ".in");
            Path stdoutFile = workDir.resolve("case-" + i + ".out");
            Path stderrFile = workDir.resolve("case-" + i + ".err");

            // 输入写入文件
            Files.writeString(
                    inputFile,
                    inputList.get(i),
                    StandardCharsets.UTF_8
            );

            // 配置运行进程
            ProcessBuilder runProcessBuilder = new ProcessBuilder(
                    javaPath.toString(),
                    "-Dfile.encoding=UTF-8",
                    "-cp", workDir.toString(),
                    "Main"
            );
            // 设置工作目录
            runProcessBuilder.directory(workDir.toFile());
            // 运行程序
            ProcessResult runResult = ProcessUtils.run(runProcessBuilder, inputFile,
                    stdoutFile, stderrFile, "运行");
            processResults.add(runResult);
        }

        return processResults;
    }

    /**
     * 删除工作目录
     * 目录中可能有 .java .class 多个输入，输出文件，将这些删除完后，再删除目录
     *
     * @param workDir
     */
    public void deleteWorkDir(Path workDir) throws IOException {

        Files.walkFileTree(
                workDir,
                new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attrs
                    ) throws IOException {

                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path dir,
                            IOException error
                    ) throws IOException {

                        if (error != null) {
                            throw error;
                        }

                        Files.deleteIfExists(dir);
                        return FileVisitResult.CONTINUE;
                    }
                }
        );
    }

}
