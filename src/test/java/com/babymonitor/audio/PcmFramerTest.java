package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The framing maths on its own, with no process and no hardware: windows must
 * overlap by the hop, straddle chunk boundaries, and report a root-mean-square
 * the silence gate can act on.
 */
class PcmFramerTest {

    @Test
    void emitsOverlappingWindowsOnceAFullFrameExists() {
        PcmFramer framer = new PcmFramer(4, 2);

        List<AudioFrame> frames = framer.accept(pcm(1, 2, 3, 4, 5, 6, 7, 8));

        assertEquals(3, frames.size(), "8 samples at frame 4 / hop 2 make 3 windows");
        assertArrayEquals(new float[] {s(1), s(2), s(3), s(4)}, frames.get(0).samples(), 1e-9f);
        assertArrayEquals(new float[] {s(3), s(4), s(5), s(6)}, frames.get(1).samples(), 1e-9f);
        assertArrayEquals(new float[] {s(5), s(6), s(7), s(8)}, frames.get(2).samples(), 1e-9f);
    }

    @Test
    void keepsPartialSamplesBetweenChunks() {
        PcmFramer framer = new PcmFramer(2, 1);

        assertTrue(framer.accept(pcm(1)).isEmpty(), "one sample cannot fill a two-sample window");

        List<AudioFrame> frames = framer.accept(pcm(2, 3, 4));
        assertEquals(3, frames.size(), "the carried sample plus three more make windows 1-2, 2-3 and 3-4");
        assertArrayEquals(new float[] {s(1), s(2)}, frames.get(0).samples(), 1e-9f);
        assertArrayEquals(new float[] {s(3), s(4)}, frames.get(2).samples(), 1e-9f);
    }

    @Test
    void carriesAnOddByteAcrossTheBoundary() {
        PcmFramer framer = new PcmFramer(1, 1);

        assertTrue(framer.accept(new byte[] {0x10}).isEmpty(), "half a sample is not a window");

        List<AudioFrame> frames = framer.accept(new byte[] {0x27, 0x00, 0x00});
        assertEquals(2, frames.size(), "the split sample plus the next one make two single-sample windows");
        assertEquals(10000f / 32768f, frames.get(0).samples()[0], 1e-9f);
        assertEquals(0f, frames.get(1).samples()[0], 1e-9f);
    }

    @Test
    void theSilenceGateSeparatesQuietFromAudible() {
        AudioFrame quiet = new PcmFramer(4, 4).accept(pcm(0, 0, 0, 0)).get(0);
        assertEquals(0.0, quiet.rms(), 1e-9);
        assertFalse(quiet.loud(0.005), "silence must not clear the gate");

        AudioFrame audible = new PcmFramer(4, 4).accept(pcm(10000, -10000, 10000, -10000)).get(0);
        assertTrue(audible.loud(0.005), "a full-scale square wave must clear the gate");
        assertEquals(10000 / 32768.0, audible.rms(), 1e-6);
    }

    @Test
    void rejectsSenselessFraming() {
        assertThrows(IllegalArgumentException.class, () -> new PcmFramer(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PcmFramer(4, 0));
        assertThrows(IllegalArgumentException.class, () -> new PcmFramer(4, 5));
    }

    /** One signed 16-bit sample as a float in [-1, 1]. */
    private static float s(int sample) {
        return sample / 32768f;
    }

    /** little-endian s16le, the layout the capture tools are asked to produce. */
    private static byte[] pcm(int... samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int sample : samples) {
            buffer.putShort((short) sample);
        }
        return buffer.array();
    }
}
