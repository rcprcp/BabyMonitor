package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single application setting, stored as a tag/value pair.
 *
 * <p>Maps the {@code babymonitor.settings} table created by {@code db/init.sql}.
 * There is no surrogate key: {@code tag} is the primary key.
 */
@Entity
@Table(name = "settings", schema = "babymonitor")
public class Setting extends PanacheEntityBase {

    @Id
    @Column(name = "tag", length = 500, nullable = false)
    public String tag;

    @Column(name = "value", length = 500, nullable = false)
    public String value;

    public Setting() {
    }

    public Setting(String tag, String value) {
        this.tag = tag;
        this.value = value;
    }

    /** Looks up a setting by tag, returning {@code null} when it is absent. */
    public static Setting findByTag(String tag) {
        return find("tag", tag).firstResult();
    }
}
