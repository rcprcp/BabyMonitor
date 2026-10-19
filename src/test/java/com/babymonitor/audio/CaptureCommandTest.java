package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The capture argv must match each tool's own shape: SoX and arecord by
 * convention, ffmpeg by {@code -f alsa -i ... -f s16le -}. All of them have to
 * end on stdout, because that is the PCM pipe the service reads.
 */
class CaptureCommandTest {

    @Test
    void soxReadsAlsaAndWritesRawToStdout() {
        List<String> args = CaptureCommand.arguments("/usr/bin/sox", "hw:2,0", 16000);

        assertTrue(args.contains("alsa"));
        assertTrue(args.contains("hw:2,0"));
        assertTrue(args.containsAll(List.of("-e", "signed", "-b", "16")));
        assertEquals("-", args.get(args.size() - 1), "output must go to stdout");
    }

    @Test
    void ffmpegUsesTheAlsaDemuxerAndRawOutput() {
        List<String> args = CaptureCommand.arguments("/usr/bin/ffmpeg", "hw:2,0", 16000);

        int format = args.indexOf("-f");
        assertEquals("alsa", args.get(format + 1));
        assertTrue(args.contains("s16le"));
        assertEquals("-", args.get(args.size() - 1), "output must go to stdout");
    }

    @Test
    void arecordUsesItsOwnFlags() {
        List<String> args = CaptureCommand.arguments("/usr/bin/arecord", "hw:2,0", 16000);

        assertTrue(args.containsAll(List.of("-D", "hw:2,0", "-f", "S16_LE", "-t", "raw")));
        assertEquals("-", args.get(args.size() - 1), "output must go to stdout");
    }

    @Test
    void theToolIsRecognisedByItsFileName() {
        assertEquals("sox", CaptureCommand.toolName("/usr/bin/sox"));
        assertEquals("ffmpeg", CaptureCommand.toolName("ffmpeg"));
        assertFalse(CaptureCommand.arguments("/usr/bin/sox", "hw:2,0", 16000).contains("-f"));
    }
}
