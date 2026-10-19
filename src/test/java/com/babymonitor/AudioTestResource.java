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
 * Points the audio settings at a stub before the app boots, so the tests never
 * open a real microphone.
 *
 * <p>Like {@link MotionTestResource}, it copies {@code fake-capture.sh} out of
 * the classpath (a jar entry is not executable) and repoints {@code binary} and
 * {@code base-dir} at a scratch directory. The window and hop are left at the
 * model's own 0.975 s / 0.48 s: a classification window must be exactly what
 * YAMNet was built for, and the stub streams PCM flat out, so there is nothing
 * to gain by shrinking them. Only the gate and the alert limits are changed, so
 * a test can see several windows and both sides of a cooldown.</p>
 */
public class AudioTestResource implements QuarkusTestResourceLifecycleManager {

    private Path directory;

    @Override
    public Map<String, String> start() {
        try {
            directory = Files.createTempDirectory("babymonitor-audio-test");
            Path binary = directory.resolve("fake-capture.sh");
            copy("/fake-capture.sh", binary);
            Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwx------"));
            return Map.of(
                    "babymonitor.audio.binary", binary.toString(),
                    "babymonitor.audio.base-dir", directory.resolve("audio").toString(),
                    "babymonitor.audio.silence-rms", "0.01",
                    // Two windows to alert and a ten second cooldown, so a test
                    // can assert both that a short run does not alert and that a
                    // sustained one alerts only once.
                    "babymonitor.audio.windows-to-alert", "2",
                    "babymonitor.audio.cooldown-seconds", "10");
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare the stub capture binary", e);
        }
    }

    @Override
    public void stop() {
        if (directory == null) {
            return;
        }
        // The stub is stopped by the tests' own AudioService.stop calls, so this
        // is usually a plain delete; anything left behind is in the temp dir.
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
        try (InputStream in = AudioTestResource.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing test resource " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
