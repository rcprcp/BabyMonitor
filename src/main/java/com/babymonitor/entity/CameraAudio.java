package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

/**
 * Per-camera audio capture configuration and preference.
 *
 * <p>{@code enabled} is the user's "Listen for cries" preference: when true the
 * camera's audio capture should run whenever the camera itself is running. It
 * survives a Stop (so turning the camera back on restores the choice) and is
 * honoured at boot; {@link com.babymonitor.audio.AudioSupervisor} only restarts
 * a capture while the camera is meant to be running. {@code device} is the ALSA
 * device passed to the capture binary (e.g. {@code hw:1,7} or
 * {@code plughw:1,0}). One row per camera exists conceptually, keyed by
 * {@code camera_number}.
 */
@Entity
@Table(name = "camera_audio", schema = "babymonitor")
public class CameraAudio extends PanacheEntityBase {

    @Id
    @Column(name = "camera_number", nullable = false)
    public Integer cameraNumber;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    @Column(name = "device", length = 100)
    public String device;

    public CameraAudio() {
    }

    public static List<CameraAudio> listAllOrdered() {
        return list("order by cameraNumber");
    }

    public static CameraAudio findByCamera(int cameraNumber) {
        return find("cameraNumber = ?1", cameraNumber).firstResult();
    }

    /**
     * The ALSA device this camera captures from: its own {@code device} when one
     * is set, otherwise the configured default. Blank is treated as unset, so an
     * empty row value does not reach the capture tool as an empty device.
     */
    public String deviceOrDefault(String fallback) {
        return device == null || device.isBlank() ? fallback : device;
    }
}
