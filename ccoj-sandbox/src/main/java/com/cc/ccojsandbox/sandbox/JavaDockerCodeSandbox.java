package com.cc.ccojsandbox.sandbox;

import com.cc.ccojsandbox.model.ExecuteCodeRequest;
import com.cc.ccojsandbox.model.ExecuteCodeResponse;
import com.cc.ccojsandbox.model.ProcessResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final int OUTPUT_BYTES = 64 * 1024;
    private static final int ARTIFACT_BYTES = 1024 * 1024;
    private static final int INPUT_BYTES = 256 * 1024;
    private static final int MAX_CASES = 64;
    private static final Duration BATCH_BUDGET = Duration.ofSeconds(85);
    private static final byte[] EMPTY = new byte[0];
    private static final Semaphore SLOTS = new Semaphore(2);

    private static final String COMPILE = """
            set -eu
            mkdir /tmp/classes
            cat > /tmp/Main.java
            timeout -k 1s 12s javac -J-Xmx256m -J-XX:ActiveProcessorCount=1 \
                -proc:none -encoding UTF-8 -d /tmp/classes /tmp/Main.java >&2 || {
                result=$?
                if [ "$result" -eq 124 ]; then exit 124; fi
                exit 10
            }
            tar -cf - -C /tmp/classes .
            """;

    private static final String RUN = """
            set -eu
            mkdir /tmp/classes
            IFS= read -r archive
            IFS= read -r input
            printf '%s' "$archive" | base64 -d > /tmp/classes.tar
            tar -xf /tmp/classes.tar -C /tmp/classes
            printf '%s' "$input" | base64 -d > /tmp/input
            exec timeout -k 1s "$1" java "$2" -XX:ActiveProcessorCount=1 \
                -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -cp /tmp/classes Main < /tmp/input
            """;

    private final DockerCommand docker;
    private final String image;

    public JavaDockerCodeSandbox() {
        this("docker", "eclipse-temurin:21-jdk-jammy");
    }

    public JavaDockerCodeSandbox(String executable, String image) {
        if (executable.isBlank() || image.isBlank() || image.startsWith("-")) {
            throw new IllegalArgumentException("Docker executable and image must be configured");
        }
        this.docker = new DockerCommand(executable);
        this.image = image;
    }

    @Override
    public ExecuteCodeResponse execute(ExecuteCodeRequest request) {
        validate(request);
        if (!SLOTS.tryAcquire()) {
            throw new IllegalStateException("Docker sandbox is busy");
        }
        try {
            long deadline = System.nanoTime() + BATCH_BUDGET.toNanos();
            var compile = phase(COMPILE, List.of(), bytes(request.getSourceCode()), 512,
                    ARTIFACT_BYTES, Duration.ofSeconds(16), deadline);
            var response = new ExecuteCodeResponse();
            response.setCompileResult(compile.toResult(false));
            response.setRunResult(List.of());
            if (compile.exitCode() != 0 || compile.limit() != null) {
                if (compile.exitCode() != 10 && compile.limit() == null) {
                    throw new IllegalStateException("Could not prepare compiled classes");
                }
                return response;
            }

            long millis = bounded(request.getTimeLimitMs(), 2000, 100, 5000);
            long memory = bounded(request.getMemoryLimitMb(), 256, 64, 512);
            String archive = Base64.getEncoder().encodeToString(compile.stdout());
            var results = new ArrayList<ProcessResult>();
            for (String input : request.getInputList()) {
                if (System.nanoTime() >= deadline) {
                    results.add(new Phase(124, EMPTY, EMPTY, "TIME").toResult(true));
                    break;
                }
                String encoded = Base64.getEncoder().encodeToString(bytes(input));
                var run = phase(RUN, List.of(java.math.BigDecimal.valueOf(millis, 3).toPlainString() + "s", "-Xmx" + (memory / 2) + "m"),
                        bytes(archive + "\n" + encoded + "\n"), memory, OUTPUT_BYTES,
                        Duration.ofMillis(millis + 5000), deadline);
                results.add(run.toResult(true));
                if (run.exitCode() != 0 || run.limit() != null) {
                    break;
                }
            }
            response.setRunResult(results);
            return response;
        } finally {
            SLOTS.release();
        }
    }

    private Phase phase(String script, List<String> arguments, byte[] input, long memoryMb,
                        int stdoutLimit, Duration timeout, long deadline) {
        String name = "ccoj-exec-" + UUID.randomUUID();
        String memory = memoryMb + "m";
        var create = new ArrayList<>(List.of("create", "--name", name, "--label", "com.ccoj.sandbox=true",
                "--pull=never", "--interactive", "--init", "--network=none", "--read-only",
                "--cap-drop=ALL", "--security-opt=no-new-privileges", "--user=65534:65534",
                "--memory=" + memory, "--memory-swap=" + memory, "--cpus=1", "--pids-limit=64",
                "--ulimit", "nofile=128:128", "--log-driver=none",
                "--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=16m,mode=1777",
                "--entrypoint", "/bin/sh", image, "-c", script, "ccoj"));
        create.addAll(arguments);
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return new Phase(124, EMPTY, EMPTY, "TIME");
            var created = docker.run(create, EMPTY, 4096, 4096,
                    Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(5).toNanos())));
            if (created.exitCode() != 0 || created.limit() != null) {
                throw new IllegalStateException("Docker container creation failed: " + text(created.stderr()));
            }
            remaining = deadline - System.nanoTime();
            if (remaining <= 0) return new Phase(124, EMPTY, EMPTY, "TIME");
            var attached = docker.run(List.of("start", "--attach", "--interactive", name), input,
                    stdoutLimit, OUTPUT_BYTES, Duration.ofNanos(Math.min(remaining, timeout.toNanos())));
            if (attached.limit() != null) {
                return new Phase(-1, attached.stdout(), attached.stderr(), attached.limit());
            }

            var state = docker.run(List.of("inspect", "--format",
                    "{{.State.Status}} {{.State.ExitCode}} {{.State.OOMKilled}} {{.State.Error}}", name),
                    EMPTY, 4096, 4096, Duration.ofSeconds(4));
            if (state.exitCode() != 0 || state.limit() != null) {
                throw new IllegalStateException("Could not inspect execution container");
            }
            var parts = text(state.stdout()).strip().split(" ", 4);
            if (parts.length != 3 || !"exited".equals(parts[0])) {
                throw new IllegalStateException("Execution container did not exit cleanly");
            }
            int exit = Integer.parseInt(parts[1]);
            String limit = "true".equals(parts[2]) ? "MEMORY" : exit == 124 ? "TIME" : null;
            return new Phase(exit, attached.stdout(), attached.stderr(), limit);
        } finally {
            boolean interrupted = Thread.interrupted();
            try {
                var removed = docker.run(List.of("rm", "-f", name), EMPTY, 4096, 4096, Duration.ofSeconds(5));
                if (removed.limit() != null || (removed.exitCode() != 0
                        && !text(removed.stderr()).contains("No such container"))) {
                    log.error("Could not remove execution container {}", name);
                }
            } catch (RuntimeException exception) {
                log.error("Could not remove execution container {}", name, exception);
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    private static void validate(ExecuteCodeRequest request) {
        if (request == null || request.getSourceCode() == null || request.getSourceCode().isBlank()
                || bytes(request.getSourceCode()).length > OUTPUT_BYTES
                || request.getInputList() == null || request.getInputList().isEmpty()
                || request.getInputList().size() > MAX_CASES) {
            throw new IllegalArgumentException("Invalid Docker sandbox request");
        }
        long inputBytes = 0;
        for (String input : request.getInputList()) {
            if (input == null || (inputBytes += bytes(input).length) > INPUT_BYTES) {
                throw new IllegalArgumentException("Invalid or oversized test input");
            }
        }
    }

    private static long bounded(Long value, long fallback, long minimum, long maximum) {
        return value == null || value <= 0 ? fallback : Math.clamp(value, minimum, maximum);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private record Phase(int exitCode, byte[] stdout, byte[] stderr, String limit) {
        ProcessResult toResult(boolean includeOutput) {
            var result = new ProcessResult();
            result.setExitCode(exitCode);
            result.setStdout(includeOutput ? text(stdout) : "");
            result.setStderr(text(stderr));
            result.setLimitExceeded(limit);
            return result;
        }
    }
}
