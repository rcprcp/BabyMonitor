package com.babymonitor.environment;

import com.babymonitor.entity.CameraLight;
import com.babymonitor.entity.EnvironmentReading;
import com.babymonitor.entity.EnvironmentThresholds;
import com.babymonitor.entity.EventType;
import com.babymonitor.service.NotificationService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jboss.logging.Logger;

/**
 * Turns a nursery reading outside the Environment page's bounds into an
 * {@link EventType#Environment} notification.
 *
 * <p>Readings are written by the sensor outside this API, so a periodic check
 * looks at the newest row and compares it with the stored bounds. The row's id
 * is recorded in the thresholds row, so each sample is checked exactly once and
 * a restart does not re-alert the sample already seen.</p>
 *
 * <p>The notification goes through {@link NotificationService}, so it reaches
 * the feed, the live event stream and the chime exactly as an Audio or Motion
 * alert does.</p>
 */
@ApplicationScoped
public class EnvironmentSupervisor {

    private static final Logger LOG = Logger.getLogger(EnvironmentSupervisor.class);

    @Inject
    NotificationService notifications;

    /** The timer entry point; {@link #checkOnce()} is the testable body. */
    @Scheduled(every = "{babymonitor.environment.check-interval:60s}")
    void scheduledCheck() {
        try {
            checkOnce();
        } catch (RuntimeException e) {
            LOG.errorf("Environment check failed: %s", e.getMessage());
        }
    }

    /** Checks the newest reading, if it has not been seen before. */
    public void checkOnce() {
        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentReading reading = EnvironmentReading.newestFirst(1).stream()
                    .findFirst()
                    .orElse(null);
            if (reading == null) {
                return;
            }
            EnvironmentThresholds bounds = EnvironmentThresholds.current();
            long lastChecked = bounds.lastCheckedId == null ? 0L : bounds.lastCheckedId;
            if (reading.id <= lastChecked) {
                return;
            }
            // When the camera light sensor is in charge of brightness, leave
            // that breach to CameraLightSupervisor so the two do not double-alert.
            CameraLight light = CameraLight.current();
            CameraLightBands lightBands = light == null ? null : CameraLightBands.from(light);
            boolean cameraLightActive =
                    light != null && light.enabled && lightBands != null && lightBands.calibrated();
            // Record the sample before notifying: both writes share this
            // transaction, so a failure in either leaves the sample unchecked
            // for the next tick rather than silently skipped.
            bounds.lastCheckedId = reading.id;
            String description = describe(reading, bounds, !cameraLightActive);
            if (description != null) {
                notifications.add(EventType.Environment, description, null, Instant.now());
            }
        });
    }

    /** The out-of-bounds sentence for a reading, or {@code null} when it is in range. */
    static String describe(EnvironmentReading reading, EnvironmentThresholds bounds) {
        return describe(reading, bounds, true);
    }

    /**
     * The out-of-bounds sentence for a reading, or {@code null} when it is in
     * range.
     *
     * @param includeBrightness whether the reading's brightness is part of the
     *                          check; false when the camera light sensor owns it
     */
    static String describe(
            EnvironmentReading reading, EnvironmentThresholds bounds, boolean includeBrightness) {
        List<String> breaches = new ArrayList<>();
        double temperatureF = reading.temperature.doubleValue() * 9 / 5 + 32;
        if (outside(temperatureF, bounds.temperatureLow, bounds.temperatureHigh)) {
            breaches.add(String.format(
                    Locale.ROOT,
                    "temperature %.1f°F (bounds %s–%s°F)",
                    temperatureF,
                    number(bounds.temperatureLow),
                    number(bounds.temperatureHigh)));
        }
        double humidity = reading.humidity.doubleValue();
        if (outside(humidity, bounds.humidityLow, bounds.humidityHigh)) {
            breaches.add(String.format(
                    Locale.ROOT,
                    "humidity %.1f%% (bounds %s–%s%%)",
                    humidity,
                    number(bounds.humidityLow),
                    number(bounds.humidityHigh)));
        }
        int brightness = reading.brightness;
        if (includeBrightness
                && (brightness < bounds.brightnessLow || brightness > bounds.brightnessHigh)) {
            breaches.add(String.format(
                    Locale.ROOT,
                    "brightness %d lux (bounds %d–%d lux)",
                    brightness,
                    bounds.brightnessLow,
                    bounds.brightnessHigh));
        }
        if (breaches.isEmpty()) {
            return null;
        }
        return "Environment out of bounds: " + String.join("; ", breaches) + ".";
    }

    private static boolean outside(double value, BigDecimal low, BigDecimal high) {
        return value < low.doubleValue() || value > high.doubleValue();
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
