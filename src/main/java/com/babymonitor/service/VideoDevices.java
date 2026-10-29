package com.babymonitor.service;

import com.babymonitor.dto.VideoDeviceDto;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Lists the host's video4linux capture nodes so the setup wizard can offer them
 * instead of asking a parent to know what {@code /dev/video4} means.
 *
 * <p>Reads the kernel's sysfs class, not {@code v4l2-ctl}: the wizard only needs
 * the node names, and this avoids spawning a process from a request. Nodes whose
 * {@code /dev} entry does not exist are skipped, so a stale sysfs entry cannot
 * be offered. Both paths are configurable so the behaviour can be tested without
 * a camera.</p>
 */
@ApplicationScoped
public class VideoDevices {

    private static final Pattern NODE = Pattern.compile("video\\d+");

    @ConfigProperty(name = "babymonitor.video.sysfs-dir", defaultValue = "/sys/class/video4linux")
    String sysfsDir;

    @ConfigProperty(name = "babymonitor.video.dev-dir", defaultValue = "/dev")
    String devDir;

    /** The capture nodes, ordered by number; empty when there are none. */
    public List<VideoDeviceDto> list() {
        Path classDir = Path.of(sysfsDir);
        Path deviceDir = Path.of(devDir);
        if (!Files.isDirectory(classDir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(classDir)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(entry -> NODE.matcher(entry.getFileName().toString()).matches())
                    .map(entry -> new VideoDeviceDto("/dev/" + entry.getFileName(), readName(entry)))
                    .filter(device -> Files.exists(deviceDir.resolve(Path.of(device.device()).getFileName())))
                    .sorted(Comparator.comparingInt(device -> number(device.device())))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + classDir, e);
        }
    }

    private static String readName(Path entry) {
        try {
            Path name = entry.resolve("name");
            return Files.isReadable(name) ? Files.readString(name).trim() : null;
        } catch (IOException e) {
            // A node with no readable name is still usable: show the path only.
            return null;
        }
    }

    private static int number(String device) {
        String digits = device.substring(device.lastIndexOf("video") + "video".length());
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
