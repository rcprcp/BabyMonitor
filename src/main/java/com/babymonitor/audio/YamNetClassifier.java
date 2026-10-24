package com.babymonitor.audio;

import ai.djl.Device;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.logging.Logger;

/**
 * YAMNet, run inside this JVM through DJL's TFLite engine.
 *
 * <p>The model and its class map are bundled as classpath resources, and are
 * loaded on the first camera that needs them rather than at boot, so an install
 * that captures no audio never pays for a model it does not use. TFLite maps
 * the model file rather than reading it into a byte array, so the resource is
 * unpacked to a temporary file once.</p>
 *
 * <p>DJL predictors are not thread-safe, and behind them the engine keeps one
 * TFLite interpreter per model that is not thread-safe either. A {@link Session}
 * -- its own predictor and NDManager -- is handed out per capture, and a single
 * lock serialises the native inference so two camera threads can never be inside
 * the shared interpreter at once. At ~2 windows/s per camera the queue is
 * invisible.</p>
 *
 * <p>YAMNet scores all 521 classes at once and the whole ranked list is
 * returned; deciding which of them mean "cry" is {@link CryDetector}'s job, so
 * narrowing the alert list stays a configuration change.</p>
 */
@ApplicationScoped
public class YamNetClassifier implements AudioClassifier {

    private static final Logger LOG = Logger.getLogger(YamNetClassifier.class);

    @Inject
    AudioConfig config;

    private final Object loadLock = new Object();

    /**
     * The TFLite interpreter inside a {@link ZooModel} is shared by every
     * predictor and is <em>not</em> thread-safe. One lock serialises inference
     * across cameras: at roughly two windows per second per camera the wait is
     * invisible, and it is what keeps two capture threads out of the same native
     * interpreter (which segfaults).
     */
    private final ReentrantLock inferenceLock = new ReentrantLock();

    /** Set once the container starts shutting the model down. */
    private volatile boolean closed;

    /** The shared, immutable once loaded model; {@code null} until first use. */
    private volatile ZooModel<NDList, NDList> model;

    /** Display names, position-indexed by class. */
    private volatile List<String> labels;

    @Override
    public Session openSession() {
        inferenceLock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("YAMNet is shutting down");
            }
            ZooModel<NDList, NDList> loaded = load();
            NDManager manager = loaded.getNDManager().newSubManager();
            return new YamNetSession(this, loaded.newPredictor(), manager, labels, config.frameSamples());
        } finally {
            inferenceLock.unlock();
        }
    }

    /** Releases the model when the application shuts down. */
    @PreDestroy
    void close() {
        closed = true;
        // Wait for any in-flight prediction before freeing the native
        // interpreter: closing it under a running inference is a use-after-free.
        inferenceLock.lock();
        try {
            ZooModel<NDList, NDList> loaded = model;
            if (loaded != null) {
                loaded.close();
            }
        } finally {
            inferenceLock.unlock();
        }
    }

    /** Loads the model and class map once, however many cameras ask at once. */
    private ZooModel<NDList, NDList> load() {
        ZooModel<NDList, NDList> loaded = model;
        if (loaded != null) {
            return loaded;
        }
        synchronized (loadLock) {
            if (model != null) {
                return model;
            }
            long started = System.nanoTime();
            Path file = unpack(config.modelResource());
            try {
                labels = readLabels(config.labelsResource());
                model = Criteria.builder()
                        .setTypes(NDList.class, NDList.class)
                        .optModelPath(file)
                        .optEngine("TFLite")
                        .optDevice(Device.cpu())
                        .build()
                        .loadModel();
            } catch (IOException | ModelException | RuntimeException e) {
                throw new IllegalStateException("Could not load " + config.modelResource(), e);
            }
            LOG.infof("Loaded YAMNet with %d classes in %.1f s",
                    labels.size(), (System.nanoTime() - started) / 1_000_000_000.0);
            return model;
        }
    }

    /** Copies a classpath resource to a temporary file TFLite can map. */
    private Path unpack(String resource) {
        try (InputStream in = YamNetClassifier.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing model resource " + resource);
            }
            Path file = Files.createTempFile("yamnet-", ".tflite");
            file.toFile().deleteOnExit();
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not unpack " + resource, e);
        }
    }

    private List<String> readLabels(String resource) throws IOException {
        try (InputStream in = YamNetClassifier.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing class map resource " + resource);
            }
            return YamNetLabels.read(in);
        }
    }

    /** One camera's predictor; belongs to the thread reading that camera's PCM. */
    private static final class YamNetSession implements Session {

        private final YamNetClassifier owner;
        private final Predictor<NDList, NDList> predictor;
        private final NDManager manager;
        private final List<String> labels;
        private final int frameSamples;

        YamNetSession(
                YamNetClassifier owner,
                Predictor<NDList, NDList> predictor,
                NDManager manager,
                List<String> labels,
                int frameSamples) {
            this.owner = owner;
            this.predictor = predictor;
            this.manager = manager;
            this.labels = labels;
            this.frameSamples = frameSamples;
        }

        @Override
        public List<AudioClassification> classify(float[] samples) {
            if (samples.length != frameSamples) {
                throw new IllegalArgumentException(
                        "YAMNet needs " + frameSamples + " samples but got " + samples.length);
            }
            float[] scores;
            // The interpreter behind every predictor is one shared, non-thread-safe
            // native object, so only one camera may be inside it at a time. The
            // lock also waits out a shutdown that is closing the model.
            owner.inferenceLock.lock();
            try {
                if (owner.closed) {
                    throw new IllegalStateException("YAMNet is shutting down");
                }
                scores = infer(samples);
            } finally {
                owner.inferenceLock.unlock();
            }
            List<AudioClassification> ranked = new ArrayList<>(scores.length);
            for (int index = 0; index < scores.length; index++) {
                ranked.add(new AudioClassification(index, name(index), scores[index]));
            }
            ranked.sort(Comparator.comparingDouble(AudioClassification::confidence).reversed());
            return ranked;
        }

        private float[] infer(float[] samples) {
            // The model takes [1, samples] waveform_binary input. Both arrays are
            // closed explicitly: this runs twice a second per camera for as long
            // as the camera is on, so anything left open is a leak, not a blip.
            try (NDArray raw = manager.create(samples);
                    NDArray input = raw.reshape(1, samples.length)) {
                NDList output = predictor.predict(new NDList(input));
                try {
                    return output.singletonOrThrow().toFloatArray();
                } finally {
                    output.close();
                }
            } catch (TranslateException e) {
                throw new IllegalStateException("YAMNet inference failed", e);
            }
        }

        private String name(int index) {
            return index < labels.size() ? labels.get(index) : "class " + index;
        }

        @Override
        public void close() {
            predictor.close();
            manager.close();
        }
    }
}
