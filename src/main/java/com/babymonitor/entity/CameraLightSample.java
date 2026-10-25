package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One calibrated camera-light probe, kept as history for the Environment page.
 *
 * <p>The single-row {@link CameraLight} table holds the latest sample and the
 * calibration; this table holds the series. Only probes taken while calibrated
 * are recorded, and calibration captures are deliberately not recorded.</p>
 */
@Entity
@Table(name = "camera_light_samples", schema = "babymonitor")
public class CameraLightSample extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "sampled_at", nullable = false)
    public Instant sampledAt;

    @Column(name = "luma", nullable = false)
    public Double luma;

    /** The stable band number, 1 (dark) through 6 (very bright). */
    @Column(name = "band", nullable = false)
    public Integer band;

    @Column(name = "proxy_lux", nullable = false)
    public Integer proxyLux;

    public CameraLightSample() {
    }

    public CameraLightSample(Instant sampledAt, Double luma, Integer band, Integer proxyLux) {
        this.sampledAt = sampledAt;
        this.luma = luma;
        this.band = band;
        this.proxyLux = proxyLux;
    }
}
