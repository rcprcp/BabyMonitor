package com.babymonitor.environment;

import com.babymonitor.dto.CameraLightBandDto;
import com.babymonitor.dto.CameraLightDto;
import com.babymonitor.dto.CameraLightSampleDto;
import com.babymonitor.dto.UpdateCameraLightRequest;
import com.babymonitor.entity.Camera;
import com.babymonitor.entity.CameraLight;
import com.babymonitor.entity.CameraLightSample;
import com.babymonitor.entity.Setting;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Probes a UVC camera's brightness at a fixed manual exposure.
 *
 * <p>The FHD camera's auto-exposure value is not exposed by its driver, so the
 * probe switches to manual mode, sets a fixed exposure, reads one frame from
 * the camera's existing MJPEG stream and computes its mean luma, then restores
 * auto-exposure. The camera must be running: the stream is the frame source, so
 * the probe never opens the device while Motion owns it.</p>
 *
 * <p>The probe briefly changes the live exposure. It is intended for an
 * occasional sample (and for calibration captures), not for a continuous
 * 30 fps measurement.</p>
 */
@ApplicationScoped
public class CameraLightSensor {

    private static final Logger LOG = Logger.getLogger(CameraLightSensor.class);
    private static final Pattern YAVG = Pattern.compile("lavfi\\.signalstats\\.YAVG=([0-9.]+)");

    @Inject
    LightConfig config;

    @Inject
    EntityManager em;

    /** The current configuration, calibration and last sample. */
    public CameraLightDto currentDto() {
        return QuarkusTransaction.requiringNew().call(() -> toDto(CameraLight.current()));
    }

    /** Saves the configuration and calibration. */
    public CameraLightDto update(UpdateCameraLightRequest request) {
        return QuarkusTransaction.requiringNew().call(() -> {
            CameraLight row = CameraLight.current();
            // The calibration only changes how future probes map to a band; the
            // band numbers already stored in camera_light_samples stay valid.
            row.cameraNumber = request.cameraNumber();
            row.exposureMs = request.exposureMs();
            row.enabled = request.enabled();
            row.darkLuma = request.darkLuma();
            row.veryDimLuma = request.veryDimLuma();
            row.dimLuma = request.dimLuma();
            row.moderateLuma = request.moderateLuma();
            row.brightLuma = request.brightLuma();
            row.veryBrightLuma = request.veryBrightLuma();
            // Keep the stored bands and the last sample; only clear the alert
            // dedup state so a fresh excursion can alert after a recalibration.
            row.lastAlertedBand = null;
            return toDto(row);
        });
    }

    /**
     * Takes one probe: manual fixed exposure, one frame from the stream, mean
     * luma, then restore auto-exposure. The probe is appended to the history.
     *
     * @throws IllegalStateException when the camera is not running, has no
     *                               device/port, or the probe tools fail
     */
    public CameraLightSampleDto sampleNow() {
        return sampleNow(true);
    }

    /**
     * Takes one probe, optionally appending it to the history.
     *
     * @param record whether this probe belongs in the history; calibration
     *               captures pass {@code false} so known lighting does not
     *               distort the Environment chart
     * @throws IllegalStateException when the camera is not running, has no
     *                               device/port, or the probe tools fail
     */
    public CameraLightSampleDto sampleNow(boolean record) {
        Probe probe = QuarkusTransaction.requiringNew().call(this::loadProbe);
        double luma;
        try {
            setManual(probe.device(), probe.exposureMs());
            luma = measure(probe.url());
        } finally {
            // Restore auto-exposure even if setting the manual controls failed
            // halfway through, so a bad probe cannot leave the live stream dark.
            restoreAuto(probe.device());
        }
        BrightnessBand band = probe.bands() == null ? null : probe.bands().bandFor(luma);
        Integer bandNumber = band == null ? null : band.number();
        Integer lux = band == null ? null : band.proxyLux();
        Instant now = Instant.now();
        QuarkusTransaction.requiringNew().run(() -> {
            CameraLight row = CameraLight.current();
            row.lastLuma = luma;
            row.lastBand = bandNumber;
            row.lastLux = lux;
            row.lastSampleAt = now;
            if (record && bandNumber != null && lux != null) {
                new CameraLightSample(now, luma, bandNumber, lux).persist();
            }
        });
        return new CameraLightSampleDto(luma, bandNumber, lux, now.toString());
    }

    /**
     * The latest calibrated band in each hour for the last {@code hours} hours,
     * oldest first. Uncalibrated probes are not recorded, so a gap means the
     * sensor was not calibrated or not sampling.
     */
    public List<CameraLightBandDto> hourlyBands(int hours) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                select to_char(
                           date_trunc('hour', sampled_at at time zone 'UTC'),
                           'YYYY-MM-DD"T"HH24:MI:SS"Z"') as hour,
                       (array_agg(band order by sampled_at desc))[1] as band,
                       (array_agg(proxy_lux order by sampled_at desc))[1] as proxy_lux
                from babymonitor.camera_light_samples
                where sampled_at >= now() - (? * interval '1 hour')
                group by date_trunc('hour', sampled_at at time zone 'UTC')
                order by date_trunc('hour', sampled_at at time zone 'UTC')
                """).setParameter(1, hours).getResultList());
        return rows.stream()
                .map(row -> new CameraLightBandDto(
                        (String) row[0],
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).intValue()))
                .toList();
    }

    private Probe loadProbe() {
        CameraLight row = CameraLight.current();
        List<Camera> parameters = Camera.listByCamera(row.cameraNumber);
        if (parameters.isEmpty()) {
            throw new IllegalStateException("No such camera: " + row.cameraNumber);
        }
        String device = parameter(parameters, "video_device");
        String port = parameter(parameters, "stream_port");
        Setting base = Setting.findByTag("stream_url");
        if (device == null || device.isBlank()) {
            throw new IllegalStateException(
                    "Camera " + row.cameraNumber + " has no video_device parameter");
        }
        if (port == null || port.isBlank() || base == null || base.value.isBlank()) {
            throw new IllegalStateException(
                    "Camera " + row.cameraNumber + " has no stream_port/stream_url");
        }
        String url = base.value.replaceAll("/+$", "") + ":" + port;
        return new Probe(
                row.cameraNumber,
                row.exposureMs,
                device,
                url,
                CameraLightBands.from(row));
    }

    private void setManual(String device, int exposureMs) {
        run(List.of(config.v4l2Ctl(), "-d", device, "--set-ctrl", "auto_exposure=1"));
        run(List.of(
                config.v4l2Ctl(),
                "-d",
                device,
                "--set-ctrl",
                "exposure_time_absolute=" + (exposureMs * 10)));
    }

    private void restoreAuto(String device) {
        try {
            run(List.of(config.v4l2Ctl(), "-d", device, "--set-ctrl", "auto_exposure=3"));
        } catch (RuntimeException e) {
            // A failed restore must not hide the sample; log it loudly.
            LOG.warnf("Could not restore auto-exposure on %s: %s", device, e.getMessage());
        }
    }

    private double measure(String url) {
        String output = run(List.of(
                config.ffmpeg(),
                "-hide_banner",
                "-loglevel",
                "info",
                "-i",
                url,
                "-frames:v",
                "1",
                "-vf",
                "signalstats,metadata=print",
                "-f",
                "null",
                "-"));
        Matcher matcher = YAVG.matcher(output);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not read a frame's luma from " + url);
        }
        return Double.parseDouble(matcher.group(1));
    }

    private String run(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "Timed out running " + command.get(0) + " after " + config.timeoutSeconds() + "s");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        command.get(0) + " failed: " + output.strip());
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not run " + command.get(0) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted running " + command.get(0), e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String parameter(List<Camera> parameters, String key) {
        return parameters.stream()
                .filter(row -> key.equals(row.paramKey))
                .map(row -> row.value)
                .findFirst()
                .orElse(null);
    }

    private static CameraLightDto toDto(CameraLight row) {
        return new CameraLightDto(
                row.cameraNumber,
                row.exposureMs,
                row.enabled,
                row.darkLuma,
                row.veryDimLuma,
                row.dimLuma,
                row.moderateLuma,
                row.brightLuma,
                row.veryBrightLuma,
                row.lastLuma,
                row.lastBand,
                row.lastLux,
                row.lastSampleAt == null ? null : row.lastSampleAt.toString());
    }

    private record Probe(
            int cameraNumber, int exposureMs, String device, String url, CameraLightBands bands) {
    }
}
