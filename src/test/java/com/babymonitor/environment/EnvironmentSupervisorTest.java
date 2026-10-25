package com.babymonitor.environment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.entity.EnvironmentReading;
import com.babymonitor.entity.EnvironmentThresholds;
import com.babymonitor.entity.EventType;
import com.babymonitor.entity.Notification;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A reading outside the Environment page's bounds becomes an Environment alert. */
@QuarkusTest
class EnvironmentSupervisorTest {

    @Inject
    EnvironmentSupervisor supervisor;

    private Long readingId;

    @BeforeEach
    void prepare() {
        resetThresholds();
        deleteEnvironmentAlerts();
        markExistingReadingsChecked();
    }

    @AfterEach
    void cleanUp() {
        if (readingId != null) {
            QuarkusTransaction.requiringNew().run(() -> EnvironmentReading.deleteById(readingId));
        }
        deleteEnvironmentAlerts();
        resetThresholds();
        markExistingReadingsChecked();
    }

    @Test
    void anOutOfBoundsReadingCreatesAnEnvironmentAlert() {
        readingId = insertReading(30.0, 20.0, 2500);

        supervisor.checkOnce();

        Notification alert = environmentAlert();
        assertNotNull(alert, "the reading should raise an Environment notification");
        assertTrue(alert.description.contains("temperature"), alert.description);
        assertTrue(alert.description.contains("humidity"), alert.description);
        assertTrue(alert.description.contains("brightness"), alert.description);
    }

    @Test
    void anInBoundsReadingDoesNotAlert() {
        readingId = insertReading(22.0, 50.0, 1000);

        supervisor.checkOnce();

        assertNull(environmentAlert(), "a reading inside the bounds is not an alert");
    }

    private static Long insertReading(double temperatureC, double humidity, int brightness) {
        EnvironmentReading reading = new EnvironmentReading();
        reading.eventTime = Instant.now();
        reading.temperature = BigDecimal.valueOf(temperatureC);
        reading.humidity = BigDecimal.valueOf(humidity);
        reading.brightness = brightness;
        QuarkusTransaction.requiringNew().run(reading::persist);
        return QuarkusTransaction.requiringNew().call(() -> EnvironmentReading.newestFirst(1)
                .stream()
                .findFirst()
                .map(row -> row.id)
                .orElse(null));
    }

    private static Notification environmentAlert() {
        return QuarkusTransaction.requiringNew()
                .call(() -> Notification.find("eventType = ?1", EventType.Environment).firstResult());
    }

    private static void deleteEnvironmentAlerts() {
        QuarkusTransaction.requiringNew()
                .run(() -> Notification.delete("eventType = ?1", EventType.Environment));
    }

    private static void markExistingReadingsChecked() {
        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentThresholds row = EnvironmentThresholds.current();
            EnvironmentReading newest = EnvironmentReading.newestFirst(1).stream()
                    .findFirst()
                    .orElse(null);
            row.lastCheckedId = newest == null ? 0L : newest.id;
        });
    }

    private static void resetThresholds() {
        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentThresholds row = EnvironmentThresholds.current();
            row.humidityLow = BigDecimal.valueOf(30);
            row.humidityHigh = BigDecimal.valueOf(75);
            row.temperatureLow = BigDecimal.valueOf(66);
            row.temperatureHigh = BigDecimal.valueOf(76);
            row.brightnessLow = 200;
            row.brightnessHigh = 2000;
        });
    }
}
