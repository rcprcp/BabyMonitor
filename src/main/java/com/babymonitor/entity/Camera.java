package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.List;

/**
 * One parameter of one camera, as stored in {@code babymonitor.cameras}.
 *
 * <p>The table has no surrogate id: it is keyed by camera and parameter name,
 * so {@link CameraId} is the composite primary key. {@code enabled} is the
 * camera's persisted desired state and is carried on each of its parameter
 * rows, so starting or stopping a camera touches every row it owns.
 *
 * <p>Maps a row of the table created by the Flyway migrations and backs the
 * Camera1 / Camera2 pages.
 */
@Entity
@Table(name = "cameras", schema = "babymonitor")
@IdClass(CameraId.class)
public class Camera extends PanacheEntityBase {

    /** The camera this parameter belongs to (1, 2, ...). */
    @Id
    @Column(name = "camera_number", nullable = false)
    public Integer cameraNumber;

    /**
     * Parameter name, e.g. {@code resolution} or {@code stream_url}.
     *
     * <p>The column is {@code key}; the field is renamed here because
     * {@code key} is a reserved word in JPQL/HQL.
     */
    @Id
    @Column(name = "key", length = 500, nullable = false)
    public String paramKey;

    @Column(name = "value", length = 500, nullable = false)
    public String value;

    /**
     * The camera's persisted desired state: {@code true} means "this camera
     * should be running". Repeated on every row it owns. The Motion supervisor
     * reads it to decide which cameras to keep alive.
     */
    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    public Camera() {
    }

    /** Every parameter of every camera, ordered by camera then parameter. */
    public static List<Camera> listAllOrdered() {
        return list("order by cameraNumber, paramKey");
    }

    /** One camera's parameters, ordered by parameter name. */
    public static List<Camera> listByCamera(int cameraNumber) {
        return list("cameraNumber = ?1 order by paramKey", cameraNumber);
    }

    /** A single parameter, or {@code null} when the camera does not define it. */
    public static Camera findOne(int cameraNumber, String paramKey) {
        return find("cameraNumber = ?1 and paramKey = ?2", cameraNumber, paramKey).firstResult();
    }
}
