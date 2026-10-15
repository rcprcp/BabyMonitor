package com.babymonitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the parts of {@link RecordingsService} that decide what a
 * request is allowed to read and which bytes it gets. They need no database, so
 * the service is instantiated directly.
 */
class RecordingsServiceTest {

    private final RecordingsService recordings = new RecordingsService();

    // --- resolve: a recording name must stay inside the camera's directory ---

    @Test
    void resolveReturnsAnExistingFile(@TempDir Path directory) throws IOException {
        Path clip = Files.writeString(directory.resolve("clip.mp4"), "video");
        assertEquals(clip, recordings.resolve(directory, "clip.mp4"));
    }

    @Test
    void resolveReturnsNullForAMissingFile(@TempDir Path directory) {
        assertNull(recordings.resolve(directory, "missing.mp4"));
    }

    @Test
    void resolveRefusesNamesThatAreNotPlainFileNames(@TempDir Path directory) {
        for (String name : new String[] {
            null, "", "  ", ".", "..", "a/b", "a\\b", "/etc/passwd", "..\u0000.mp4",
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> recordings.resolve(directory, name),
                    "should refuse: " + name);
        }
    }

    @Test
    void resolveRefusesADirectory(@TempDir Path directory) throws IOException {
        Files.createDirectory(directory.resolve("sub"));
        assertNull(recordings.resolve(directory, "sub"));
    }

    // --- parseRange: only a single, intersecting byte range is served ---------

    @Test
    void parseRangeHandlesTheThreeRangeForms() {
        assertEquals(new RecordingsService.ByteRange(0, 99), RecordingsService.parseRange("bytes=0-99", 1000));
        assertEquals(new RecordingsService.ByteRange(100, 999), RecordingsService.parseRange("bytes=100-", 1000));
        assertEquals(new RecordingsService.ByteRange(900, 999), RecordingsService.parseRange("bytes=-100", 1000));
    }

    @Test
    void parseRangeClampsAnOpenEndToTheFile() {
        assertEquals(new RecordingsService.ByteRange(0, 999), RecordingsService.parseRange("bytes=0-100000", 1000));
        assertEquals(new RecordingsService.ByteRange(0, 999), RecordingsService.parseRange("bytes=-100000", 1000));
    }

    @Test
    void parseRangeIgnoresHeadersItCannotUse() {
        assertNull(RecordingsService.parseRange(null, 1000));
        assertNull(RecordingsService.parseRange("items=0-9", 1000));
        assertNull(RecordingsService.parseRange("bytes=0-9,20-29", 1000));
        assertNull(RecordingsService.parseRange("bytes=abc", 1000));
        assertNull(RecordingsService.parseRange("bytes=-", 1000));
    }

    @Test
    void parseRangeRejectsARangeOutsideTheFile() {
        assertThrows(IllegalArgumentException.class, () -> RecordingsService.parseRange("bytes=1000-", 1000));
        assertThrows(IllegalArgumentException.class, () -> RecordingsService.parseRange("bytes=50-40", 1000));
        assertThrows(IllegalArgumentException.class, () -> RecordingsService.parseRange("bytes=0-", 0));
    }

    // --- urlFor: links are usable path segments -------------------------------

    @Test
    void urlForEncodesTheName() {
        assertEquals("/api/cameras/2/recordings/a%20b.mp4", RecordingsService.urlFor(2, "a b.mp4"));
        assertEquals("/api/cameras/1/recordings/clip.mp4", RecordingsService.urlFor(1, "clip.mp4"));
    }
}
