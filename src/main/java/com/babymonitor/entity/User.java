package com.babymonitor.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A login account.
 *
 * <p>Maps the {@code babymonitor.users} table created by {@code db/init.sql}.
 * {@code passwordHash} is a bcrypt hash; see {@code PasswordHasher}.
 */
@Entity
@Table(name = "users", schema = "babymonitor")
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "name", length = 500, nullable = false, unique = true)
    public String name;

    @Column(name = "password_hash", length = 500, nullable = false)
    public String passwordHash;

    /** UI preference: {@code light}, {@code dark} or {@code auto} (follow the OS). */
    @Column(name = "theme", length = 500, nullable = false)
    public String theme = "auto";

    /** Accent color for the UI: a Mantine palette name such as {@code blue}. */
    @Column(name = "theme_color", length = 500, nullable = false)
    public String themeColor = "blue";

    /** Notification prompt sound: a file prefix in {@code frontend/public/sounds/}. */
    @Column(name = "prompt_sound", length = 500, nullable = false)
    public String promptSound = "chime";

    /** Live Stream page active camera number. */
    @Column(name = "active_camera", length = 500, nullable = false, columnDefinition = "integer default 1")
    public int activeCameraInt = 1;

    public User() {
    }

    public User(String name, String passwordHash) {
        this.name = name;
        this.passwordHash = passwordHash;
    }

    /** Looks up a user by name, returning {@code null} when it is absent. */
    public static User findByName(String name) {
        return find("name", name).firstResult();
    }
}
