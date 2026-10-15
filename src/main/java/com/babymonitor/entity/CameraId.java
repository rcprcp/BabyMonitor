package com.babymonitor.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link Camera}: {@code (camera_number, key)}.
 *
 * <p>The field names must match the entity's id fields ({@code cameraNumber}
 * and {@code paramKey}) for the JPA {@code @IdClass} mapping to line up.
 */
public class CameraId implements Serializable {

    private Integer cameraNumber;
    private String paramKey;

    public CameraId() {
    }

    public CameraId(Integer cameraNumber, String paramKey) {
        this.cameraNumber = cameraNumber;
        this.paramKey = paramKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CameraId id)) {
            return false;
        }
        return Objects.equals(cameraNumber, id.cameraNumber)
                && Objects.equals(paramKey, id.paramKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cameraNumber, paramKey);
    }
}
