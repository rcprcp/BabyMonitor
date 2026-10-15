package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One ambient reading from the nursery sensor.
 *
 * <p>Maps a row of the {@code babymonitor.environment} table created by
 * {@code db/init.sql}. The rows form a time series: the Environment page charts
 * and lists them newest first.
 */
@Entity
@Table(name = "environment", schema = "babymonitor")
public class EnvironmentReading extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    /** When the sample was taken (not when the row was written). */
    @Column(name = "event_time", nullable = false)
    public Instant eventTime;

    /** Air temperature in degrees Celsius. */
    @Column(name = "temperature", nullable = false)
    public BigDecimal temperature;

    /** Relative humidity as a percentage (0-100). */
    @Column(name = "humidity", nullable = false)
    public BigDecimal humidity;

    /** Light level in lux. */
    @Column(name = "brightness", nullable = false)
    public Integer brightness;

    public EnvironmentReading() {
    }

    /**
     * The most recent samples, newest first.
     *
     * @param limit how many rows to return
     */
    public static List<EnvironmentReading> newestFirst(int limit) {
        return find("order by eventTime desc").page(0, limit).list();
    }
}
