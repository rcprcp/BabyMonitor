package com.babymonitor.environment;

import com.babymonitor.dto.CameraLightDto;
import com.babymonitor.dto.CameraLightSampleDto;
import com.babymonitor.entity.CameraLight;
import com.babymonitor.entity.EnvironmentThresholds;
import com.babymonitor.entity.EventType;
import com.babymonitor.service.NotificationService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Objects;
import org.jboss.logging.Logger;

/**
 * Samples the camera light sensor and raises an {@link EventType#Environment}
 * alert when its band's proxy lux is outside the Environment page's brightness
 * bounds.
 *
 * <p>Alerts are per band transition: a run of samples in the same out-of-bounds
 * band notifies once, and the next in-bounds sample clears the remembered band
 * so a later excursion alerts again. This keeps a 60-second sampler from
 * flooding the feed while still reporting a real change.</p>
 *
 * <p>When this sensor is enabled and calibrated, {@link EnvironmentSupervisor}
 * leaves brightness to this class and only checks the sensor reading's
 * temperature and humidity, so the two do not double-alert.</p>
 */
@ApplicationScoped
public class CameraLightSupervisor {

    private static final Logger LOG = Logger.getLogger(CameraLightSupervisor.class);

    @Inject
    CameraLightSensor sensor;

    @Inject
    NotificationService notifications;

    /** The last probe failure, so a stopped camera does not log every tick. */
    private String lastProbeError;

    /** The timer entry point; {@link #checkOnce()} is the testable body. */
    @Scheduled(every = "{babymonitor.light.check-interval:60s}")
    void scheduledCheck() {
        try {
            checkOnce();
        } catch (RuntimeException e) {
            LOG.errorf("Camera light check failed: %s", e.getMessage());
        }
    }

    /** Takes a probe when the sensor is enabled and calibrated, then evaluates it. */
    public void checkOnce() {
        CameraLightDto config = sensor.currentDto();
        CameraLightBands bands = CameraLightBands.from(config);
        if (!config.enabled() || bands == null || !bands.calibrated()) {
            return;
        }
        CameraLightSampleDto sample;
        try {
            sample = sensor.sampleNow();
        } catch (RuntimeException e) {
            // The camera may be stopped; log a distinct failure once, not every
            // tick, and retry on the next one.
            String message = e.getMessage();
            if (!Objects.equals(message, lastProbeError)) {
                LOG.warnf("Camera light probe failed: %s", message);
                lastProbeError = message;
            }
            return;
        }
        lastProbeError = null;
        evaluate(sample);
    }

    /** Applies the brightness bounds to one probe result. Package-private for tests. */
    void evaluate(CameraLightSampleDto sample) {
        EnvironmentThresholds bounds =
                QuarkusTransaction.requiringNew().call(EnvironmentThresholds::current);
        boolean outside = sample.lux() != null
                && sample.band() != null
                && (sample.lux() < bounds.brightnessLow || sample.lux() > bounds.brightnessHigh);
        QuarkusTransaction.requiringNew().run(() -> {
            CameraLight row = CameraLight.current();
            if (!outside) {
                row.lastAlertedBand = null;
                return;
            }
            if (Objects.equals(sample.band(), row.lastAlertedBand)) {
                return;
            }
            row.lastAlertedBand = sample.band();
            notifications.add(
                    EventType.Environment,
                    description(sample, bounds),
                    null,
                    Instant.now());
        });
    }

    private static String description(CameraLightSampleDto sample, EnvironmentThresholds bounds) {
        return "Camera light is " + BrightnessBand.labelForNumber(sample.band()) + " (≈"
                + sample.lux() + " lux; bounds " + bounds.brightnessLow + "–"
                + bounds.brightnessHigh + " lux).";
    }
}
