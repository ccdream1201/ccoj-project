package com.cc.ccojsandbox.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class DockerCommand {
    private static final byte[] EMPTY = new byte[0];
    private final String executable;

    DockerCommand(String executable) {
        this.executable = executable;
    }

    Result run(List<String> args, byte[] input, int stdoutLimit, int stderrLimit, Duration timeout) {
        var command = new ArrayList<String>();
        command.add(executable);
        command.addAll(args);

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot start Docker CLI", exception);
        }

        var threads = Executors.newVirtualThreadPerTaskExecutor();
        var oversized = new AtomicBoolean();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            var stdout = threads.submit(() -> read(process.getInputStream(), stdoutLimit, oversized, process));
            var stderr = threads.submit(() -> read(process.getErrorStream(), stderrLimit, oversized, process));
            threads.submit(() -> {
                try (var stdin = process.getOutputStream()) {
                    stdin.write(input);
                } catch (IOException ignored) {
                    // A process that exits early can close its input before the entire payload is written.
                }
            });
            if (!process.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                return new Result(-1, EMPTY, EMPTY, oversized.get() ? "OUTPUT" : "TIME");
            }
            if (oversized.get()) return new Result(-1, EMPTY, EMPTY, "OUTPUT");
            byte[] out = await(stdout, deadline);
            byte[] err = await(stderr, deadline);
            return new Result(process.exitValue(), out, err, oversized.get() ? "OUTPUT" : null);
        } catch (TimeoutException exception) {
            return new Result(-1, EMPTY, EMPTY, oversized.get() ? "OUTPUT" : "TIME");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Docker execution interrupted", exception);
        } catch (ExecutionException exception) {
            if (oversized.get()) return new Result(-1, EMPTY, EMPTY, "OUTPUT");
            throw new IllegalStateException("Cannot read Docker output", exception.getCause());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            threads.shutdownNow();
        }
    }

    private static byte[] await(Future<byte[]> stream, long deadline)
            throws InterruptedException, ExecutionException, TimeoutException {
        return stream.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    private static byte[] read(InputStream input, int limit, AtomicBoolean oversized, Process process)
            throws IOException {
        try (input; var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            for (int size; (size = input.read(buffer)) != -1; ) {
                if (output.size() + size > limit) {
                    oversized.set(true);
                    process.destroyForcibly();
                    break;
                }
                output.write(buffer, 0, size);
            }
            return output.toByteArray();
        }
    }

    record Result(int exitCode, byte[] stdout, byte[] stderr, String limit) {}
}
