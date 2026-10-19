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
 * <p>DJL predictors are not thread-safe and the capture loop runs a reader
 * thread per camera, so the model is shared but a {@link Session} -- its own
 * predictor and NDManager -- is handed out per capture. Nothing here is
 * synchronized on the inference path, so cameras do not queue behind each
 * other.</p>
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

    /** The shared, immutable once loaded model; {@code null} until first use. */
    private volatile ZooModel<NDList, NDList> model;

    /** Display names, position-indexed by class. */
    private volatile List<String> labels;

    @Override
    public Session openSession() {
        ZooModel<NDList, NDList> loaded = load();
        NDManager manager = loaded.getNDManager().newSubManager();
        return new YamNetSession(loaded.newPredictor(), manager, labels, config.frameSamples());
    }

    /** Releases the model when the application shuts down. */
    @PreDestroy
    void close() {
        ZooModel<NDList, NDList> loaded = model;
        if (loaded != null) {
            loaded.close();
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

        private final Predictor<NDList, NDList> predictor;
        private final NDManager manager;
        private final List<String> labels;
        private final int frameSamples;

        YamNetSession(
                Predictor<NDList, NDList> predictor, NDManager manager, List<String> labels, int frameSamples) {
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
            float[] scores = infer(samples);
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
