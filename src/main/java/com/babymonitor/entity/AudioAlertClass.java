package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

/**
 * One YAMNet class's alert switch.
 *
 * <p>The class map (names, indices and the category ranges the UI groups them
 * by) lives in the bundled CSV; this table only records which classes are
 * allowed to raise an {@code Audio} notification. The row for every class is
 * created by the migration, so the config page can toggle them one at a time
 * without inventing a class.</p>
 */
@Entity
@Table(name = "audio_alert_classes", schema = "babymonitor")
public class AudioAlertClass extends PanacheEntityBase {

    @Id
    @Column(name = "class_index", nullable = false)
    public Integer classIndex;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    public AudioAlertClass() {
    }

    public static List<AudioAlertClass> listOrdered() {
        return list("order by classIndex");
    }

    public static AudioAlertClass findByIndex(int index) {
        return find("classIndex = ?1", index).firstResult();
    }
}
