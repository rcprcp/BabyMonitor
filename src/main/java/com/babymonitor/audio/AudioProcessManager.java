package com.babymonitor.audio;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;

/**
 * Owns the per-camera audio capture processes.
 *
 * <p>Like {@link com.babymonitor.motion.MotionProcessManager} it records the
 * child's pid, so a restarted JVM can recognise a process it did not start
 * instead of spawning a duplicate. The one important difference is stdout:
 * Motion's diagnostics are merged into a log file, but a capture child's stdout
 * <em>is</em> the PCM feed, so it is left as a pipe for {@link AudioService} to
 * read and stderr is redirected to a per-camera log file instead.</p>
 */
@ApplicationScoped
public class AudioProcessManager {

    private static final Logger LOG = Logger.getLogger(AudioProcessManager.class);
    private static final Duration STOP_GRACE = Duration.ofSeconds(5);

    @Inject
    AudioConfig config;

    private final Map<Integer, Process> started = new ConcurrentHashMap<>();

    /** The running capture process's id for a camera, or {@code null} when stopped. */
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

    /**
     * Starts the capture process for a camera and returns it so the caller can
     * read its stdout.
     *
     * <p>The caller has already checked it is not running.</p>
     *
     * @param device the ALSA device to capture, e.g. {@code hw:2,0}
     * @throws IOException when the process cannot be started
     */
    public Process start(int cameraNumber, String device) throws IOException {
        Path errorFile = errorFile(cameraNumber);
        Files.createDirectories(errorFile.getParent());
        List<String> argv = new ArrayList<>();
        argv.add(config.binary());
        argv.addAll(CaptureCommand.arguments(config.binary(), device, config.sampleRate()));
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectError(ProcessBuilder.Redirect.appendTo(errorFile.toFile()));
        Process process = builder.start();
        started.put(cameraNumber, process);
        writePidFile(cameraNumber, process.pid());
        LOG.infof("Started audio capture for camera %d (pid %d, %s)", cameraNumber, process.pid(), device);
        return process;
    }

    /** Stops a camera's capture, if any. Returns false if it was not running. */
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
        LOG.infof("Stopped audio capture for camera %d (pid %d)", cameraNumber, pid);
        return true;
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
        return directory(cameraNumber).resolve("audio.pid");
    }

    private Path errorFile(int cameraNumber) {
        return directory(cameraNumber).resolve("audio.err");
    }

    private Path directory(int cameraNumber) {
        return Path.of(config.baseDir()).resolve(Integer.toString(cameraNumber));
    }
}
