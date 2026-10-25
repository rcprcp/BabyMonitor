package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * The high/low bounds the Environment page edits.
 *
 * <p>A single row (id 1) holds the pair for each metric. Temperature is stored
 * in Fahrenheit because that is how the page presents it; the readings in
 * {@link EnvironmentReading} remain Celsius.</p>
 */
@Entity
@Table(name = "environment_thresholds", schema = "babymonitor")
public class EnvironmentThresholds extends PanacheEntityBase {

    @Id
    public Integer id;

    @Column(name = "humidity_low", nullable = false)
    public BigDecimal humidityLow;

    @Column(name = "humidity_high", nullable = false)
    public BigDecimal humidityHigh;

    @Column(name = "temperature_low", nullable = false)
    public BigDecimal temperatureLow;

    @Column(name = "temperature_high", nullable = false)
    public BigDecimal temperatureHigh;

    @Column(name = "brightness_low", nullable = false)
    public Integer brightnessLow;

    @Column(name = "brightness_high", nullable = false)
    public Integer brightnessHigh;

    /** The newest environment reading id already checked by EnvironmentSupervisor. */
    @Column(name = "last_checked_id", nullable = false)
    public Long lastCheckedId;

    public EnvironmentThresholds() {
    }

    /** The single thresholds row, created with the shipped defaults if missing. */
    public static EnvironmentThresholds current() {
        EnvironmentThresholds row = find("id = ?1", 1).firstResult();
        if (row == null) {
            row = new EnvironmentThresholds();
            row.id = 1;
            row.humidityLow = BigDecimal.valueOf(30);
            row.humidityHigh = BigDecimal.valueOf(75);
            row.temperatureLow = BigDecimal.valueOf(66);
            row.temperatureHigh = BigDecimal.valueOf(76);
            row.brightnessLow = 200;
            row.brightnessHigh = 2000;
            row.lastCheckedId = 0L;
            row.persist();
        }
        return row;
    }
}
