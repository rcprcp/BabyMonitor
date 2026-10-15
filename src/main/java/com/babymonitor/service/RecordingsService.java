package com.babymonitor.service;

import com.babymonitor.dto.RecordingDto;
import com.babymonitor.entity.Camera;
import com.babymonitor.motion.MotionConfig;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Locates the recordings Motion writes for each camera and turns them into links
 * the app can serve.
 *
 * <p>A camera's clips live in its {@code target_dir} row, which is what Motion
 * was started with, so that row is the single source of truth; when a camera
 * does not define one, the default layout {@code &lt;base&gt;/&lt;n&gt;/recordings}
 * is assumed. Nothing here is specific to Motion beyond that, so the recordings
 * are read back exactly as they were written.
 *
 * <p>{@link #resolve} refuses any name that is not a plain file name, so a
 * request cannot climb out of the camera's directory.
 */
@ApplicationScoped
public class RecordingsService {

    @Inject
    MotionConfig config;

    /**
     * The directory a camera's recordings live in, or {@code null} when there is
     * no such camera.
     */
    public Path directoryFor(int cameraNumber) {
        List<Camera> rows = QuarkusTransaction.requiringNew()
                .call(() -> Camera.listByCamera(cameraNumber));
        if (rows.isEmpty()) {
            return null;
        }
        String targetDir = rows.stream()
                .filter(row -> "target_dir".equals(row.paramKey))
                .map(row -> row.value)
                .findFirst()
                .orElse(null);
        Path directory = (targetDir == null || targetDir.isBlank())
                ? Path.of(config.baseDir(), Integer.toString(cameraNumber), "recordings")
                : Path.of(targetDir);
        return directory.toAbsolutePath().normalize();
    }

    /** Every recording in a directory, newest first. A missing directory is empty. */
    public List<RecordingDto> list(int cameraNumber, Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparingLong(RecordingsService::lastModified).reversed())
                    .map(file -> toDto(cameraNumber, file))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + directory, e);
        }
    }

    /**
     * Resolves a recording name inside a camera's directory, refusing anything
     * that would escape it.
     *
     * @return the file, or {@code null} when no such recording exists
     * @throws IllegalArgumentException when the name is not a plain file name
     */
    public Path resolve(Path directory, String fileName) {
        if (fileName == null || fileName.isBlank()
                || ".".equals(fileName) || "..".equals(fileName)
                || fileName.indexOf('/') >= 0
                || fileName.indexOf('\\') >= 0
                || fileName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Not a recording name: " + fileName);
        }
        Path file = directory.resolve(fileName).normalize();
        if (file.getParent() == null || !file.getParent().equals(directory)) {
            throw new IllegalArgumentException("Not a recording name: " + fileName);
        }
        return Files.isRegularFile(file) ? file : null;
    }

    /** Where the SPA fetches a recording from. */
    public static String urlFor(int cameraNumber, String fileName) {
        return "/api/cameras/" + cameraNumber + "/recordings/"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Parses a single-range {@code Range: bytes=…} header against a file of
     * {@code size} bytes.
     *
     * @return the range to send, or {@code null} when the header is absent or
     *         unsupported (the caller should send the whole file)
     * @throws IllegalArgumentException when the header asks for a range that does
     *         not intersect the file
     */
    public static ByteRange parseRange(String header, long size) {
        if (header == null) {
            return null;
        }
        String value = header.strip();
        if (!value.startsWith("bytes=")) {
            return null;
        }
        String spec = value.substring("bytes=".length()).strip();
        // Multiple ranges in one request are legal HTTP but not worth the
        // multipart/byteranges machinery here; ignoring the header is allowed.
        if (spec.isEmpty() || spec.indexOf(',') >= 0) {
            return null;
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String from = spec.substring(0, dash).strip();
        String to = spec.substring(dash + 1).strip();

        long start;
        long end;
        try {
            if (from.isEmpty()) {
                if (to.isEmpty()) {
                    return null;
                }
                long suffix = Long.parseLong(to);
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else {
                start = Long.parseLong(from);
                end = to.isEmpty() ? size - 1 : Long.parseLong(to);
            }
        } catch (NumberFormatException e) {
            return null;
        }

        if (size <= 0 || start < 0 || start >= size || end < start) {
            throw new IllegalArgumentException("Range not satisfiable");
        }
        return new ByteRange(start, Math.min(end, size - 1));
    }

    /**
     * Opens the slice of a file a range describes. The caller closes the stream,
     * which also closes the underlying channel.
     */
    public static InputStream openRange(Path file, ByteRange range) throws IOException {
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
        try {
            channel.position(range.start());
            return new BoundedInputStream(Channels.newInputStream(channel), range.length());
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    private static RecordingDto toDto(int cameraNumber, Path file) {
        String name = file.getFileName().toString();
        return new RecordingDto(name, urlFor(cameraNumber, name), size(file), modified(file));
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    private static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String modified(Path file) {
        return Instant.ofEpochMilli(lastModified(file)).toString();
    }

    /** An inclusive byte range, as asked for by a {@code Range} header. */
    public record ByteRange(long start, long end) {

        public long length() {
            return end - start + 1;
        }
    }

    /** Reads at most {@code remaining} bytes from the stream it wraps. */
    private static final class BoundedInputStream extends FilterInputStream {

        private long remaining;

        private BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = super.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            long skipped = super.skip(Math.min(count, remaining));
            remaining -= skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(super.available(), remaining);
        }
    }
}
