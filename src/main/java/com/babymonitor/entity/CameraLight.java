package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The camera-derived light sensor's configuration, calibration and last sample.
 *
 * <p>A single row (id 1). The camera's device and stream port are read from
 * {@code babymonitor.cameras}; this row only holds what is specific to the
 * light probe: which camera, the fixed exposure, whether it is running, the six
 * calibration lumas and the most recent result.</p>
 */
@Entity
@Table(name = "camera_light", schema = "babymonitor")
public class CameraLight extends PanacheEntityBase {

    @Id
    public Integer id;

    @Column(name = "camera_number", nullable = false)
    public Integer cameraNumber;

    /** The fixed manual exposure used for a probe, in milliseconds. */
    @Column(name = "exposure_ms", nullable = false)
    public Integer exposureMs;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    @Column(name = "dark_luma")
    public Double darkLuma;

    @Column(name = "very_dim_luma")
    public Double veryDimLuma;

    @Column(name = "dim_luma")
    public Double dimLuma;

    @Column(name = "moderate_luma")
    public Double moderateLuma;

    @Column(name = "bright_luma")
    public Double brightLuma;

    @Column(name = "very_bright_luma")
    public Double veryBrightLuma;

    @Column(name = "last_luma")
    public Double lastLuma;

    @Column(name = "last_band", length = 20)
    public String lastBand;

    @Column(name = "last_lux")
    public Integer lastLux;

    @Column(name = "last_sample_at")
    public Instant lastSampleAt;

    /** The band that last raised an alert, so the same band does not repeat. */
    @Column(name = "last_alerted_band", length = 20)
    public String lastAlertedBand;

    public CameraLight() {
    }

    /** The single sensor row, created with the defaults if missing. */
    public static CameraLight current() {
        CameraLight row = find("id = ?1", 1).firstResult();
        if (row == null) {
            row = new CameraLight();
            row.id = 1;
            row.cameraNumber = 1;
            row.exposureMs = 20;
            row.enabled = false;
            row.persist();
        }
        return row;
    }
}
