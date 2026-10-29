package com.babymonitor.service;

import com.babymonitor.audio.AudioConfig;
import com.babymonitor.audio.AudioService;
import com.babymonitor.entity.Camera;
import com.babymonitor.entity.CameraAudio;
import com.babymonitor.motion.MotionService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Starts every camera when the application boots, and the audio capture of
 * every camera whose "Listen for cries" preference is on.
 *
 * <p>The app runs as a baby monitor, so a reboot must not leave it silently
 * watching nothing. This observer runs after the Motion and audio cold-start
 * resets ({@link MotionService#resetOnStartup} and
 * {@link AudioService#resetOnStartup}), which kill leftover child processes,
 * then marks every camera as on and brings it up. A Stop is therefore "until
 * the next boot": that is deliberate, and the Camera page says so.</p>
 *
 * <p>Audio is different: {@code camera_audio.enabled} is the user's preference,
 * so a camera whose audio was switched off stays silent across a reboot. This
 * observer only fills in a missing row (default on) and starts the captures
 * whose preference is on.</p>
 *
 * <p>Set {@code babymonitor.start-cameras-on-boot=false} to restore the older
 * cold start, where nothing runs until the user starts a camera from its
 * page.</p>
 */
@ApplicationScoped
public class CameraStartup {

    private static final Logger LOG = Logger.getLogger(CameraStartup.class);

    @Inject
    MotionService motion;

    @Inject
    AudioService audio;

    @Inject
    AudioConfig audioConfig;

    @ConfigProperty(name = "babymonitor.start-cameras-on-boot", defaultValue = "true")
    boolean startCamerasOnBoot;

    /** Boot entry point; runs after the services' own cold-start resets. */
    void onStart(@Observes @Priority(200) StartupEvent event) {
        if (startCamerasOnBoot) {
            startAll();
        }
    }

    /** Starts every camera, whatever the persisted state said, plus its wanted audio. */
    void startAll() {
        Map<Integer, List<Camera>> cameras = readCameras();
        if (cameras.isEmpty()) {
            return;
        }

        // Mark everything on first: if a process cannot start, the supervisors
        // see the desired state and keep retrying with their normal backoff.
        QuarkusTransaction.requiringNew().run(() -> Camera.update("set enabled = true"));

        for (Map.Entry<Integer, List<Camera>> camera : cameras.entrySet()) {
            int cameraNumber = camera.getKey();
            try {
                motion.ensureRunning(cameraNumber, MotionService.toParameters(camera.getValue()));
                LOG.infof("Started Motion for camera %d at boot", cameraNumber);
            } catch (RuntimeException e) {
                LOG.errorf(
                        "Could not start Motion for camera %d at boot: %s",
                        cameraNumber,
                        e.getMessage());
            }
        }

        Map<Integer, String> devices = wantedAudio(cameras.keySet());
        for (Map.Entry<Integer, String> device : devices.entrySet()) {
            int cameraNumber = device.getKey();
            try {
                // AudioService.start() is a no-op when the capture is already up.
                audio.start(cameraNumber, device.getValue());
                LOG.infof("Started audio for camera %d at boot", cameraNumber);
            } catch (RuntimeException e) {
                LOG.errorf(
                        "Could not start audio for camera %d at boot: %s",
                        cameraNumber,
                        e.getMessage());
            }
        }
    }

    private static Map<Integer, List<Camera>> readCameras() {
        List<Camera> rows = QuarkusTransaction.requiringNew().call(Camera::listAllOrdered);
        Map<Integer, List<Camera>> byCamera = new TreeMap<>();
        for (Camera row : rows) {
            byCamera.computeIfAbsent(row.cameraNumber, number -> new ArrayList<>()).add(row);
        }
        return byCamera;
    }

    /**
     * Creates a switched-on audio row for every camera that has none and returns
     * the ALSA device of every camera whose audio preference is on. Existing
     * rows are left alone: audio is the one part of the camera state that is a
     * preference rather than a run state.
     */
    private Map<Integer, String> wantedAudio(Set<Integer> cameraNumbers) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Map<Integer, String> devices = new LinkedHashMap<>();
            for (Integer cameraNumber : cameraNumbers) {
                CameraAudio row = CameraAudio.findByCamera(cameraNumber);
                if (row == null) {
                    row = new CameraAudio();
                    row.cameraNumber = cameraNumber;
                    row.enabled = true;
                    row.persist();
                }
                if (row.enabled) {
                    devices.put(cameraNumber, row.deviceOrDefault(audioConfig.device()));
                }
            }
            return devices;
        });
    }
}
