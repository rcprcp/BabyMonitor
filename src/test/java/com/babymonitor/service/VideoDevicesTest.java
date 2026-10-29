package com.babymonitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.dto.VideoDeviceDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The wizard's device picker reads sysfs, not a spawned process. */
class VideoDevicesTest {

    @TempDir
    Path temp;

    @Test
    void listsOnlyNodesThatExistAndSortsThemByNumber() throws IOException {
        Path sysfs = Files.createDirectory(temp.resolve("class"));
        Path dev = Files.createDirectory(temp.resolve("dev"));

        createNode(sysfs, "video10", "USB Camera");
        createNode(sysfs, "video2", "Integrated Camera");
        createNode(sysfs, "video0", null);
        Files.createDirectory(sysfs.resolve("not-a-video-node"));
        // A sysfs entry whose device was unplugged must not be offered.
        createNode(sysfs, "video7", "Stale camera");
        Files.createFile(dev.resolve("video0"));
        Files.createFile(dev.resolve("video2"));
        Files.createFile(dev.resolve("video10"));

        VideoDevices devices = devices(sysfs, dev);

        assertEquals(
                List.of(
                        new VideoDeviceDto("/dev/video0", null),
                        new VideoDeviceDto("/dev/video2", "Integrated Camera"),
                        new VideoDeviceDto("/dev/video10", "USB Camera")),
                devices.list());
    }

    @Test
    void returnsNothingWhenTheHostHasNoVideoClass() {
        VideoDevices devices = devices(temp.resolve("missing"), temp.resolve("dev"));

        assertTrue(devices.list().isEmpty());
    }

    private static void createNode(Path sysfs, String name, String displayName) throws IOException {
        Path node = Files.createDirectory(sysfs.resolve(name));
        if (displayName != null) {
            Files.writeString(node.resolve("name"), displayName + "\n");
        }
    }

    private static VideoDevices devices(Path sysfs, Path dev) {
        VideoDevices devices = new VideoDevices();
        devices.sysfsDir = sysfs.toString();
        devices.devDir = dev.toString();
        return devices;
    }
}
