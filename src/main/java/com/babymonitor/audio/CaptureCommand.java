package com.babymonitor.audio;

import java.nio.file.Path;
import java.util.List;

/**
 * Builds the argv that makes the configured capture tool read one ALSA device
 * and stream raw PCM on stdout.
 *
 * <p>SoX, ffmpeg and arecord take very different flags for the same job, so the
 * shape is chosen from the binary's own name. All three are asked for signed
 * 16-bit little-endian mono at the requested rate, which is exactly the layout
 * {@link PcmFramer} decodes -- keep the two in step.</p>
 *
 * <p>The tool is invoked as an external process, never linked, which is how the
 * app already treats the GPL {@code motion} binary and keeps SoX's GPL away from
 * the Apache-2.0 application.</p>
 */
public final class CaptureCommand {

    private CaptureCommand() {
    }

    /** The tool's own name, e.g. {@code sox} for {@code /usr/bin/sox}. */
    static String toolName(String binary) {
        return Path.of(binary).getFileName().toString();
    }

    /**
     * The arguments (after the binary itself) that capture {@code device} as raw
     * s16le PCM on stdout.
     *
     * @param binary     the capture executable, used only to pick the flag shape
     * @param device     the ALSA device, e.g. {@code hw:2,0} or {@code plughw:2,0}
     * @param sampleRate the rate to resample to, in hertz
     */
    public static List<String> arguments(String binary, String device, int sampleRate) {
        String rate = Integer.toString(sampleRate);
        String tool = toolName(binary);
        if (tool.startsWith("ffmpeg")) {
            return List.of(
                    "-hide_banner", "-loglevel", "error",
                    "-f", "alsa", "-i", device,
                    "-ac", "1", "-ar", rate,
                    "-f", "s16le", "-");
        }
        if (tool.startsWith("arecord")) {
            return List.of(
                    "-D", device, "-c", "1", "-r", rate,
                    "-f", "S16_LE", "-t", "raw", "-");
        }
        // SoX and compatible tools: input, then the output format, then stdout.
        return List.of(
                "-q", "-t", "alsa", device,
                "-c", "1", "-r", rate,
                "-e", "signed", "-b", "16", "-t", "raw", "-");
    }
}
