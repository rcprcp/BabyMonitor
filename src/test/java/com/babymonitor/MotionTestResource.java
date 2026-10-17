package com.babymonitor;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Map;

/**
 * Points the Motion settings at a stub before the app boots, so the tests never
 * spawn a real {@code motion}.
 *
 * <p>The app has no master switch: {@code POST /api/cameras/{n}/motion/apply}
 * genuinely writes a config and starts a process. Left alone, the tests would
 * open real cameras and write into the real base directory, so this copies
 * {@code fake-motion.sh} out of the classpath (a jar entry is not executable)
 * and repoints {@code binary}, {@code base-dir} and {@code reconcile-on-start}.
 *
 * <p>{@code reconcile-on-start} is switched off so the app does not start a
 * process behind the tests' backs; each test drives its own apply/stop and so
 * stays order-independent.
 */
public class MotionTestResource implements QuarkusTestResourceLifecycleManager {

    private Path directory;

    @Override
    public Map<String, String> start() {
        try {
            directory = Files.createTempDirectory("babymonitor-motion-test");
            Path binary = directory.resolve("fake-motion.sh");
            copy("/fake-motion.sh", binary);
            Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwx------"));
            return Map.of(
                    "babymonitor.motion.binary", binary.toString(),
                    "babymonitor.motion.base-dir", directory.resolve("motion").toString(),
                    "babymonitor.motion.reconcile-on-start", "false");
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare the stub Motion binary", e);
        }
    }

    @Override
    public void stop() {
        if (directory == null) {
            return;
        }
        // The stub is signalled by the tests' own /motion/stop calls, so this is
        // usually a plain delete; a process that outlived a failing test keeps
        // its files, and that is not worth failing the run over.
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // Left behind in the system temp dir; harmless.
                }
            });
        } catch (IOException e) {
            // As above.
        }
    }

    private static void copy(String resource, Path target) throws IOException {
        try (InputStream in = MotionTestResource.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing test resource " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
