package com.babymonitor.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.AudioTestResource;
import com.babymonitor.entity.Camera;
import com.babymonitor.entity.CameraAudio;
import com.babymonitor.entity.EventType;
import com.babymonitor.entity.Notification;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A capture the app wanted running and did not find must be brought back, and
 * the parent must be told: it is the audio twin of {@link
 * com.babymonitor.motion.MotionSupervisor}. The scheduler is off under %test, so
 * the tests drive {@link AudioSupervisor#checkOnce()} directly.
 */
@QuarkusTest
@QuarkusTestResource(AudioTestResource.class)
class AudioSupervisorTest {

    /** A camera number the app does not define, so only this test owns its row. */
    private static final int CAMERA = 12;

    @Inject
    AudioService audio;

    @Inject
    AudioSupervisor supervisor;

    @Inject
    StubClassifier classifier;

    @BeforeEach
    void resetState() {
        classifier.reset();
        audio.stop(CAMERA);
        deleteAlerts(CAMERA);
        setEnabled(CAMERA, false);
        deleteCamera(CAMERA);
    }

    @AfterEach
    void forgetCamera() {
        audio.stop(CAMERA);
        deleteCamera(CAMERA);
        deleteAlerts(CAMERA);
        setEnabled(CAMERA, false);
    }

    @Test
    void aDeadCaptureIsRestartedAndANotificationIsWritten() {
        setCameraEnabled(CAMERA, true);
        setEnabled(CAMERA, true);
        audio.start(CAMERA, "hw:12,0");
        assertTrue(audio.status(CAMERA).running(), "the capture should be up to begin with");

        // Simulate a crash: the process is gone, but the camera still wants audio.
        audio.stop(CAMERA);
        assertFalse(audio.status(CAMERA).running());

        supervisor.checkOnce();

        assertTrue(audio.status(CAMERA).running(), "the supervisor should restart the capture");
        assertNotNull(findSystemAlert(CAMERA), "a System notification should record the restart");
    }

    @Test
    void aCameraThatDoesNotWantAudioIsLeftAlone() {
        setCameraEnabled(CAMERA, true);
        setEnabled(CAMERA, false);
        audio.stop(CAMERA);

        supervisor.checkOnce();

        assertFalse(audio.status(CAMERA).running(), "a stopped camera is not the supervisor's business");
        assertNull(findSystemAlert(CAMERA), "and it raises no notification");
    }

    @Test
    void aStoppedCameraIsNotBroughtBackByItsAudioPreference() {
        // The parent stopped the camera but left "Listen for cries" on. Audio
        // must not resurrect the camera behind their back.
        setCameraEnabled(CAMERA, false);
        setEnabled(CAMERA, true);
        audio.stop(CAMERA);

        supervisor.checkOnce();

        assertFalse(audio.status(CAMERA).running(), "a stopped camera stays stopped");
        assertNull(findSystemAlert(CAMERA), "and it raises no notification");
    }

    @Test
    void theRetryBackoffWidensAndCaps() {
        AudioSupervisor.CaptureWatch watch = new AudioSupervisor.CaptureWatch();
        Instant now = Instant.ofEpochSecond(1_000_000);
        assertTrue(watch.restartDue(now), "the first restart is immediate");

        watch.onRestart(now);
        assertFalse(watch.restartDue(now.plusSeconds(1)), "a second attempt waits for the backoff");
        assertTrue(watch.restartDue(now.plus(AudioSupervisor.RETRY_BASE)));

        // However many times it keeps failing, the wait is pinned at the cap.
        for (int i = 0; i < 20; i++) {
            watch.onRestart(now);
        }
        assertFalse(watch.restartDue(now.plus(AudioSupervisor.RETRY_MAX).minusSeconds(1)));
        assertTrue(watch.restartDue(now.plus(AudioSupervisor.RETRY_MAX)));
    }

    /** The camera's own desired state, which gates audio supervision. */
    private static void setCameraEnabled(int cameraNumber, boolean enabled) {
        QuarkusTransaction.requiringNew().run(() -> {
            Camera row = Camera.find("cameraNumber = ?1", cameraNumber).firstResult();
            if (row == null) {
                row = new Camera();
                row.cameraNumber = cameraNumber;
                row.paramKey = "camera_name";
                row.value = "Supervisor test";
            }
            row.enabled = enabled;
            row.persist();
        });
    }

    private static void deleteCamera(int cameraNumber) {
        QuarkusTransaction.requiringNew().run(() -> Camera.delete("cameraNumber = ?1", cameraNumber));
    }

    private static void setEnabled(int cameraNumber, boolean enabled) {
        QuarkusTransaction.requiringNew().run(() -> {
            CameraAudio row = CameraAudio.findByCamera(cameraNumber);
            if (row == null) {
                row = new CameraAudio();
                row.cameraNumber = cameraNumber;
                row.enabled = enabled;
                row.persist();
            } else {
                row.enabled = enabled;
            }
        });
    }

    /** The stored audio-down notification for a camera, or {@code null}. */
    private static Notification findSystemAlert(int cameraNumber) {
        return QuarkusTransaction.requiringNew().call(() -> Notification.find(
                        "eventType = ?1 and description like ?2",
                        EventType.System, "%camera " + cameraNumber + " %")
                .firstResult());
    }

    private static void deleteAlerts(int cameraNumber) {
        QuarkusTransaction.requiringNew()
                .run(() -> Notification.delete("description like ?1", "%camera " + cameraNumber + " %"));
    }
}
