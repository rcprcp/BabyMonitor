package com.babymonitor.motion;

import com.babymonitor.dto.MotionStatusDto;
import com.babymonitor.entity.Camera;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.logging.Logger;

/**
 * Drives the per-camera Motion processes from the {@code babymonitor.cameras}
 * table.
 *
 * <p>Each camera's generated config is rebuilt from its rows on every
 * <em>Apply</em>: the process is started if it was not running, or sent a
 * {@code SIGHUP} to reload if it was. Stopping a camera stops its process
 * outright. The app never writes the shared template, so local edits to it
 * survive.
 *
 * <p>Startup is deliberately cold: every camera is marked not running and any
 * process left over from a previous run is killed, so nothing runs until the
 * user starts a camera from its page. A camera may be stopped, but the app can
 * always write its config and bring its process up. Once a camera is marked
 * running, {@link MotionSupervisor} keeps its process alive. Tests point
 * {@link MotionConfig} at a stub binary and a scratch directory so nothing real
 * is spawned.
 */
@ApplicationScoped
public class MotionService {

    private static final Logger LOG = Logger.getLogger(MotionService.class);
    private static final String TEMPLATE_RESOURCE = "/motion/motion.template";
    private static final String NOTIFY_SCRIPT_RESOURCE = "/motion/motion-notify.sh";

    @Inject
    MotionConfig config;

    @Inject
    MotionConfigBuilder builder;

    @Inject
    MotionProcessManager processes;

    private final Map<Integer, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** The current state of a camera's Motion process. */
    public MotionStatusDto status(int cameraNumber) {
        Path configFile = configFile(cameraNumber);
        Long pid = processes.pidOf(cameraNumber);
        return new MotionStatusDto(
                cameraNumber,
                pid != null,
                pid,
                configFile.toString(),
                Files.exists(configFile));
    }

    /** Whether a Motion process is currently running for this camera. */
    public boolean isRunning(int cameraNumber) {
        return processes.isRunning(cameraNumber);
    }

    /**
     * Rebuilds a camera's config from the given rows and brings Motion up to date:
     * start it when it is not running, otherwise {@code SIGHUP} it to reload.
     *
     * @throws IllegalArgumentException when a parameter cannot safely be written
     * @throws UncheckedIOException     when the files cannot be prepared
     */
    public MotionStatusDto apply(int cameraNumber, Map<String, String> parameters) {
        ReentrantLock lock = lockFor(cameraNumber);
        lock.lock();
        try {
            return writeAndStartLocked(cameraNumber, parameters);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare the Motion configuration", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Brings a camera up only if it is not already running, rebuilding its
     * config first. Used by {@link MotionSupervisor} to revive a camera whose
     * process has died.
     *
     * @return {@code true} when a process had to be started, {@code false} when
     *         Motion was already running and nothing was touched
     * @throws IllegalArgumentException when a parameter cannot safely be written
     * @throws UncheckedIOException     when the files cannot be prepared
     */
    public boolean ensureRunning(int cameraNumber, Map<String, String> parameters) {
        ReentrantLock lock = lockFor(cameraNumber);
        lock.lock();
        try {
            if (processes.isRunning(cameraNumber)) {
                return false;
            }
            writeAndStartLocked(cameraNumber, parameters);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare the Motion configuration", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Writes the camera's config and starts or reloads its process. Callers hold
     * the camera's lock, so no other start or stop can interleave.
     */
    private MotionStatusDto writeAndStartLocked(int cameraNumber, Map<String, String> parameters)
            throws IOException {
        prepareDirectories(cameraNumber, parameters);
        ensureSupportFiles();
        String content = builder.build(cameraNumber, readTemplate(), parameters);
        Path configFile = configFile(cameraNumber);
        writeAtomically(configFile, content);
        if (processes.isRunning(cameraNumber)) {
            processes.reload(cameraNumber);
        } else {
            processes.start(cameraNumber, configFile, outputFile(cameraNumber));
        }
        return status(cameraNumber);
    }

    /** Stops a camera's Motion, if it is running. */
    public MotionStatusDto stop(int cameraNumber) {
        processes.stop(cameraNumber);
        return status(cameraNumber);
    }

    private ReentrantLock lockFor(int cameraNumber) {
        return locks.computeIfAbsent(cameraNumber, number -> new ReentrantLock());
    }

    /**
     * Gives the app a cold start: any {@code motion} left over from a previous run
     * is killed and every camera is marked not running.
     *
     * <p>A started child outlives the JVM that spawned it, so without this a
     * dev-mode recompile would leave an orphaned process that nothing tracks and
     * the next Apply would duplicate. Desired state is deliberately not carried
     * across restarts: the user starts a camera from its own page.
     */
    void resetOnStartup(@Observes StartupEvent event) {
        if (!config.resetOnStart()) {
            return;
        }
        List<Camera> rows;
        try {
            rows = QuarkusTransaction.requiringNew().call(Camera::listAllOrdered);
        } catch (RuntimeException e) {
            LOG.warnf("Skipping the Motion reset: %s", e.getMessage());
            return;
        }
        Set<Integer> cameraNumbers = new TreeSet<>();
        for (Camera row : rows) {
            cameraNumbers.add(row.cameraNumber);
        }
        for (Integer cameraNumber : cameraNumbers) {
            try {
                stop(cameraNumber);
            } catch (RuntimeException e) {
                LOG.errorf("Could not stop leftover Motion for camera %d: %s", cameraNumber, e.getMessage());
            }
        }
        clearEnabled();
    }

    /** Marks every camera not running, so a restart always begins cold. */
    private void clearEnabled() {
        try {
            QuarkusTransaction.requiringNew().run(() -> Camera.update("set enabled = false"));
        } catch (RuntimeException e) {
            LOG.warnf("Could not mark the cameras not running: %s", e.getMessage());
        }
    }

    /** Flattens a camera's rows into the key/value map the builder and manager take. */
    public static Map<String, String> toParameters(List<Camera> rows) {
        Map<String, String> parameters = new LinkedHashMap<>();
        for (Camera row : rows) {
            parameters.put(row.paramKey, row.value);
        }
        return parameters;
    }

    private void prepareDirectories(int cameraNumber, Map<String, String> parameters) throws IOException {
        Path base = Path.of(config.baseDir());
        Path cameraDir = base.resolve(Integer.toString(cameraNumber));
        Files.createDirectories(base);
        Files.createDirectories(cameraDir);
        Files.createDirectories(cameraDir.resolve("logs"));
        createParent(parameters.get("target_dir"));
        createParent(parameters.get("log_file"));
    }

    private static void createParent(String path) throws IOException {
        if (path == null || path.isBlank()) {
            return;
        }
        Path parent = Path.of(path).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void ensureSupportFiles() throws IOException {
        Path base = Path.of(config.baseDir());
        copyIfMissing(TEMPLATE_RESOURCE, base.resolve(config.templateName()), false);
        copyIfMissing(NOTIFY_SCRIPT_RESOURCE, base.resolve(config.notifyScriptName()), true);
    }

    private static void copyIfMissing(String resource, Path target, boolean executable) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = MotionService.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing bundled resource " + resource);
            }
            Files.copy(in, target);
        }
        if (executable) {
            try {
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
            } catch (UnsupportedOperationException e) {
                // Not a POSIX filesystem: leave the copied permissions as they are.
            }
        }
    }

    private String readTemplate() throws IOException {
        Path template = Path.of(config.baseDir()).resolve(config.templateName());
        return Files.readString(template, StandardCharsets.UTF_8);
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path configFile(int cameraNumber) {
        return Path.of(config.baseDir()).resolve("motion.config." + cameraNumber);
    }

    private Path outputFile(int cameraNumber) {
        return Path.of(config.baseDir())
                .resolve(Integer.toString(cameraNumber))
                .resolve("logs")
                .resolve("motion.out");
    }
}
