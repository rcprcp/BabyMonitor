package com.babymonitor.audio;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Fans a camera's raw PCM out to browser listeners.
 *
 * <p>One capture now feeds two consumers: the in-JVM YAMNet classifier and any
 * number of live-listen streams. The reader thread must never block on a slow
 * browser, so each subscriber gets a small bounded queue and a full queue drops
 * its oldest chunk -- live audio wants the freshest samples, not every sample.
 * When a capture stops, {@link #finish(int)} ends every listener's stream so an
 * HTTP response does not hang open.</p>
 */
@ApplicationScoped
public class AudioBroadcaster {

    /** Chunks buffered per listener; enough slack for a short network hiccup. */
    private static final int QUEUE_CAPACITY = 64;

    /** Sentinel that wakes a blocked reader when the stream is over. */
    private static final byte[] END = new byte[0];

    private final Map<Integer, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();

    /** Registers a listener for one camera. The caller must unsubscribe. */
    public Subscriber subscribe(int cameraNumber) {
        Subscriber subscriber = new Subscriber();
        subscribers
                .computeIfAbsent(cameraNumber, number -> ConcurrentHashMap.newKeySet())
                .add(subscriber);
        return subscriber;
    }

    /** Removes a listener and wakes it if it is waiting for more audio. */
    public void unsubscribe(int cameraNumber, Subscriber subscriber) {
        subscribers.computeIfPresent(cameraNumber, (number, set) -> {
            set.remove(subscriber);
            return set.isEmpty() ? null : set;
        });
        subscriber.close();
    }

    /** Sends one PCM chunk to every listener; never blocks the capture thread. */
    public void publish(int cameraNumber, byte[] chunk) {
        Set<Subscriber> set = subscribers.get(cameraNumber);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (Subscriber subscriber : set) {
            subscriber.offer(chunk);
        }
    }

    /** Ends every listener's stream for a camera whose capture has stopped. */
    public void finish(int cameraNumber) {
        Set<Subscriber> set = subscribers.remove(cameraNumber);
        if (set != null) {
            set.forEach(Subscriber::close);
        }
    }

    /** Whether anyone is listening to a camera. */
    public boolean hasSubscribers(int cameraNumber) {
        Set<Subscriber> set = subscribers.get(cameraNumber);
        return set != null && !set.isEmpty();
    }

    /** One browser listener's bounded buffer. */
    public static final class Subscriber {

        private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        private volatile boolean closed;

        private Subscriber() {
        }

        private void offer(byte[] chunk) {
            if (closed) {
                return;
            }
            // Drop the oldest rather than the newest: a listener that falls
            // behind should catch up to live, not replay stale audio.
            while (!queue.offer(chunk)) {
                queue.poll();
            }
        }

        /**
         * The next PCM chunk, or {@code null} once the capture has stopped or
         * this listener has been unsubscribed.
         */
        public byte[] next() throws InterruptedException {
            byte[] chunk = queue.take();
            return chunk == END ? null : chunk;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            queue.clear();
            queue.offer(END);
        }
    }
}
