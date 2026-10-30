package com.babymonitor.motion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Stopping Motion must not return while the old process is alive: a restart
 * kills leftovers and then immediately starts replacements, and Motion does not
 * retry a stream port it failed to bind. The old stop returned as soon as it
 * escalated to SIGKILL, so the replacement raced the dying process for 8081.
 */
class MotionProcessManagerStopTest {

    private Path directory;
    private Long pid;

    @AfterEach
    void cleanUp() throws IOException {
        if (pid != null) {
            ProcessHandle.of(pid).ifPresent(handle -> {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            });
        }
        if (directory == null) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // Left in the temp dir; harmless.
                }
            });
        }
    }

    @Test
    void stopWaitsForAProcessThatIgnoresSigtermToExit() throws Exception {
        Path script = stubbornMotion();
        MotionProcessManager processes = new MotionProcessManager();
        processes.config = config(script);
        processes.webhookSecret = Optional.empty();
        processes.stopGrace = Duration.ofMillis(250);

        Path configFile = directory.resolve("motion.config.1");
        Files.writeString(configFile, "");
        processes.start(1, configFile, directory.resolve("1/logs/motion.out"));
        pid = processes.pidOf(1);
        assertNotNull(pid, "the stub should have been started");
        assertTrue(isAlive(pid), "the stub should be running before stop");

        long startedAt = System.nanoTime();
        assertTrue(processes.stop(1));
        long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(waitedMs >= 150, "stop should wait out the grace before killing");
        assertFalse(isAlive(pid), "stop must not return while Motion is still alive");
        assertFalse(processes.isRunning(1));
        assertFalse(
                Files.exists(directory.resolve("1/motion.pid")),
                "the pid file should only go once the process is gone");
    }

    /** A Motion stand-in that ignores SIGTERM, so stop has to escalate. */
    private Path stubbornMotion() throws IOException {
        directory = Files.createTempDirectory("babymonitor-motion-stop-test");
        Path script = directory.resolve("stubborn-motion.sh");
        Files.writeString(script, "#!/bin/sh\ntrap '' TERM\nwhile :; do sleep 1; done\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    private MotionConfig config(Path binary) {
        return new MotionConfig() {
            @Override
            public String binary() {
                return binary.toString();
            }

            @Override
            public String baseDir() {
                return directory.toString();
            }

            @Override
            public String templateName() {
                return "motion.template";
            }

            @Override
            public String notifyScriptName() {
                return "motion-notify.sh";
            }

            @Override
            public boolean resetOnStart() {
                return true;
            }

            @Override
            public String webhookUrl() {
                return "http://localhost:8080/api/webhooks/videoNotification";
            }

            @Override
            public String checkInterval() {
                return "15s";
            }
        };
    }

    private static boolean isAlive(Long processId) {
        return ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false);
    }
}
