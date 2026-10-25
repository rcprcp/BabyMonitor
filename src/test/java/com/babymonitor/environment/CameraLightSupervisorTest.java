package com.babymonitor.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.dto.CameraLightSampleDto;
import com.babymonitor.entity.CameraLight;
import com.babymonitor.entity.EnvironmentThresholds;
import com.babymonitor.entity.EventType;
import com.babymonitor.entity.Notification;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A camera band outside the brightness bounds becomes one Environment alert per excursion. */
@QuarkusTest
class CameraLightSupervisorTest {

    @Inject
    CameraLightSupervisor supervisor;

    @BeforeEach
    void prepare() {
        resetThresholds();
        resetCameraLight();
        deleteEnvironmentAlerts();
    }

    @AfterEach
    void cleanUp() {
        deleteEnvironmentAlerts();
        resetCameraLight();
        resetThresholds();
    }

    @Test
    void anOutOfBoundsBandAlertsOncePerExcursion() {
        supervisor.evaluate(sample(230, "very-bright", 3000));
        assertEquals(1, environmentAlertCount());
        Notification alert = environmentAlert();
        assertNotNull(alert);
        assertTrue(alert.description.contains("Very bright"), alert.description);
        assertTrue(alert.description.contains("3000"), alert.description);

        // The same band on the next tick is not a new event.
        supervisor.evaluate(sample(230, "very-bright", 3000));
        assertEquals(1, environmentAlertCount());

        // Back in bounds clears the remembered band.
        supervisor.evaluate(sample(100, "moderate", 300));
        assertEquals(1, environmentAlertCount());

        // A later excursion alerts again.
        supervisor.evaluate(sample(230, "very-bright", 3000));
        assertEquals(2, environmentAlertCount());
    }

    @Test
    void anInBoundsBandDoesNotAlert() {
        supervisor.evaluate(sample(100, "moderate", 300));
        assertEquals(0, environmentAlertCount());
    }

    private static CameraLightSampleDto sample(double luma, String band, int lux) {
        return new CameraLightSampleDto(luma, band, lux, Instant.now().toString());
    }

    private static long environmentAlertCount() {
        return QuarkusTransaction.requiringNew()
                .call(() -> Notification.count("eventType = ?1", EventType.Environment));
    }

    private static Notification environmentAlert() {
        return QuarkusTransaction.requiringNew()
                .call(() -> Notification.find("eventType = ?1", EventType.Environment).firstResult());
    }

    private static void deleteEnvironmentAlerts() {
        QuarkusTransaction.requiringNew()
                .run(() -> Notification.delete("eventType = ?1", EventType.Environment));
    }

    private static void resetThresholds() {
        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentThresholds row = EnvironmentThresholds.current();
            row.brightnessLow = 200;
            row.brightnessHigh = 2000;
        });
    }

    private static void resetCameraLight() {
        QuarkusTransaction.requiringNew().run(() -> {
            CameraLight row = CameraLight.current();
            row.enabled = false;
            row.darkLuma = null;
            row.veryDimLuma = null;
            row.dimLuma = null;
            row.moderateLuma = null;
            row.brightLuma = null;
            row.veryBrightLuma = null;
            row.lastAlertedBand = null;
        });
    }
}
