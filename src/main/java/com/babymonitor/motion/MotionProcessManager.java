package com.babymonitor.motion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Owns the {@code motion} processes the app starts, one per camera.
 *
 * <p>A process is started as {@code motion -n -c <config>} -- non-daemon, so it
 * is a plain child of this JVM. Its pid is written to the camera's data
 * directory, which is what lets a restarted JVM (dev mode recompiles constantly)
 * recognise and adopt a process it did not itself start instead of spawning a
 * duplicate.
 *
 * <p>Reloading is a {@code SIGHUP}, which makes Motion re-read its config without
 * dropping the stream; stopping is a {@code SIGTERM} (then {@code SIGKILL} if the
 * process lingers) -- matching "disabling a camera kills its motion".
 */
@ApplicationScoped
public class MotionProcessManager {

    private static final Logger LOG = Logger.getLogger(MotionProcessManager.class);
    private static final Duration STOP_GRACE = Duration.ofSeconds(5);

    @Inject
    MotionConfig config;

    @ConfigProperty(name = "babymonitor.webhooks.secret")
    Optional<String> webhookSecret;

    private final Map<Integer, Process> started = new ConcurrentHashMap<>();

    /** The running process's id for a camera, or {@code null} when it is not running. */
    public Long pidOf(int cameraNumber) {
        Process process = started.get(cameraNumber);
        if (process != null) {
            if (process.isAlive()) {
                return process.pid();
            }
            started.remove(cameraNumber);
        }
        Long pid = readPidFile(cameraNumber);
        if (pid != null && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            return pid;
        }
        return null;
    }

    public boolean isRunning(int cameraNumber) {
        return pidOf(cameraNumber) != null;
    }

    /** Starts Motion for a camera. The caller has already checked it is not running. */
    public void start(int cameraNumber, Path configFile, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        ProcessBuilder builder = new ProcessBuilder(
                config.binary(), "-n", "-c", configFile.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(outputFile.toFile()));
        builder.environment().put("BABYMONITOR_WEBHOOK_URL", config.webhookUrl());
        webhookSecret.filter(secret -> !secret.isBlank())
                .ifPresent(secret -> builder.environment().put("BABYMONITOR_WEBHOOK_SECRET", secret));
        Process process = builder.start();
        started.put(cameraNumber, process);
        writePidFile(cameraNumber, process.pid());
        LOG.infof("Started Motion for camera %d (pid %d)", cameraNumber, process.pid());
    }

    /** Asks a running Motion to re-read its config. Returns false if it was not running. */
    public boolean reload(int cameraNumber) {
        Long pid = pidOf(cameraNumber);
        return pid != null && signal("HUP", pid);
    }

    /** Stops a camera's Motion, if any. Returns false if it was not running. */
    public boolean stop(int cameraNumber) {
        Long pid = pidOf(cameraNumber);
        started.remove(cameraNumber);
        deletePidFile(cameraNumber);
        if (pid == null) {
            return false;
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty()) {
            return false;
        }
        ProcessHandle process = handle.get();
        process.destroy();
        try {
            process.onExit().get(STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            process.destroyForcibly();
        }
        LOG.infof("Stopped Motion for camera %d (pid %d)", cameraNumber, pid);
        return true;
    }

    private boolean signal(String name, long pid) {
        try {
            Process kill = new ProcessBuilder("kill", "-" + name, Long.toString(pid)).start();
            return kill.waitFor() == 0;
        } catch (IOException e) {
            LOG.warnf("Could not send SIG%s to pid %d: %s", name, pid, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Long readPidFile(int cameraNumber) {
        Path pidFile = pidFile(cameraNumber);
        try {
            if (!Files.exists(pidFile)) {
                return null;
            }
            return Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).strip());
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    private void writePidFile(int cameraNumber, long pid) {
        Path pidFile = pidFile(cameraNumber);
        try {
            Files.createDirectories(pidFile.getParent());
            Files.writeString(pidFile, pid + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warnf("Could not write %s: %s", pidFile, e.getMessage());
        }
    }

    private void deletePidFile(int cameraNumber) {
        try {
            Files.deleteIfExists(pidFile(cameraNumber));
        } catch (IOException e) {
            LOG.warnf("Could not delete the pid file for camera %d: %s", cameraNumber, e.getMessage());
        }
    }

    private Path pidFile(int cameraNumber) {
        return Path.of(config.baseDir())
                .resolve(Integer.toString(cameraNumber))
                .resolve("motion.pid");
    }
}
