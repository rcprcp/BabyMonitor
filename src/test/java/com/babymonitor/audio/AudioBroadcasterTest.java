package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The fan-out that lets a live listener share the capture YAMNet is scoring. */
class AudioBroadcasterTest {

    @Test
    void publishesChunksToSubscribers() throws InterruptedException {
        AudioBroadcaster broadcaster = new AudioBroadcaster();
        AudioBroadcaster.Subscriber subscriber = broadcaster.subscribe(1);

        byte[] chunk = {1, 2, 3, 4};
        broadcaster.publish(1, chunk);

        assertArrayEquals(chunk, subscriber.next());
        assertFalse(broadcaster.hasSubscribers(2), "another camera is unaffected");
        broadcaster.unsubscribe(1, subscriber);
        assertFalse(broadcaster.hasSubscribers(1));
    }

    @Test
    void finishingACameraEndsEveryListener() throws InterruptedException {
        AudioBroadcaster broadcaster = new AudioBroadcaster();
        AudioBroadcaster.Subscriber first = broadcaster.subscribe(1);
        AudioBroadcaster.Subscriber second = broadcaster.subscribe(1);

        broadcaster.publish(1, new byte[] {7});
        assertNotNull(first.next());
        assertNotNull(second.next());

        broadcaster.finish(1);
        assertNull(first.next());
        assertNull(second.next());
    }

    @Test
    void aSlowListenerDropsOldChunksInsteadOfBlockingTheCapture() throws InterruptedException {
        AudioBroadcaster broadcaster = new AudioBroadcaster();
        AudioBroadcaster.Subscriber subscriber = broadcaster.subscribe(1);

        // More chunks than the bounded queue holds; publishing must not block,
        // and the listener must end up with the newest audio, not the oldest.
        for (int i = 0; i < 100; i++) {
            broadcaster.publish(1, new byte[] {(byte) i});
        }

        byte[] last = null;
        byte[] chunk;
        while ((chunk = subscriber.next()) != null) {
            last = chunk;
            if ((last[0] & 0xFF) == 99) {
                break;
            }
        }
        assertNotNull(last);
        assertEquals(99, last[0] & 0xFF, "the freshest chunk is the one that survives");
        broadcaster.unsubscribe(1, subscriber);
    }
}
