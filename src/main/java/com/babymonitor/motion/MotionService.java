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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.logging.Logger;

/**
 * Drives the per-camera Motion processes from the {@code babymonitor.cameras}
 * table.
 *
 * <p>Each camera's generated config is rebuilt from its rows on every
 * <em>Apply</em>: the process is started if it was not running, or sent a
 * {@code SIGHUP} to reload if it was. Switching a camera off stops its process
 * outright. The app never writes the shared template, so local edits to it
 * survive.
 *
 * <p>There is no master switch: a camera may be stopped, but the app can always
 * write its config and bring its process up. Tests point {@link MotionConfig}
 * at a stub binary and a scratch directory so nothing real is spawned.
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

    /**
     * Rebuilds a camera's config from the given rows and brings Motion up to date:
     * start it when it is not running, otherwise {@code SIGHUP} it to reload.
     *
     * @throws IllegalArgumentException when a parameter cannot safely be written
     * @throws UncheckedIOException     when the files cannot be prepared
     */
    public MotionStatusDto apply(int cameraNumber, Map<String, String> parameters) {
        ReentrantLock lock = locks.computeIfAbsent(cameraNumber, number -> new ReentrantLock());
        lock.lock();
        try {
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
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare the Motion configuration", e);
        } finally {
            lock.unlock();
        }
        return status(cameraNumber);
    }

    /** Stops a camera's Motion, if it is running. */
    public MotionStatusDto stop(int cameraNumber) {
        processes.stop(cameraNumber);
        return status(cameraNumber);
    }

    /** Starts or stops a camera's Motion to match its enabled flag. */
    public void onEnabledChanged(int cameraNumber, boolean enabled, Map<String, String> parameters) {
        if (enabled) {
            apply(cameraNumber, parameters);
        } else {
            stop(cameraNumber);
        }
    }

    /**
     * Brings the running processes in line with the database when the app starts:
     * a process this JVM did not start (adopted through its pid file) is reloaded
     * rather than duplicated, and a disabled camera is stopped.
     */
    void reconcile(@Observes StartupEvent event) {
        if (!config.reconcileOnStart()) {
            return;
        }
        List<Camera> rows;
        try {
            rows = QuarkusTransaction.requiringNew().call(Camera::listAllOrdered);
        } catch (RuntimeException e) {
            LOG.warnf("Skipping the Motion reconciliation: %s", e.getMessage());
            return;
        }
        Map<Integer, List<Camera>> byCamera = new LinkedHashMap<>();
        for (Camera row : rows) {
            byCamera.computeIfAbsent(row.cameraNumber, number -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<Integer, List<Camera>> entry : byCamera.entrySet()) {
            int cameraNumber = entry.getKey();
            List<Camera> cameraRows = entry.getValue();
            try {
                if (cameraRows.get(0).enabled) {
                    apply(cameraNumber, toParameters(cameraRows));
                } else {
                    stop(cameraNumber);
                }
            } catch (RuntimeException e) {
                LOG.errorf("Could not reconcile Motion for camera %d: %s", cameraNumber, e.getMessage());
            }
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
