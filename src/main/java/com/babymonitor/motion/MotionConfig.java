package com.babymonitor.motion;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration of the app-managed Motion processes.
 *
 * <p>BabyMonitor owns one {@code motion} process per camera: their generated
 * configuration files live in {@link #baseDir()}, and each camera also gets a
 * data directory named after its number. Everything is driven from here rather
 * than from systemd, so the master switch {@link #enabled()} keeps development
 * and tests from spawning real processes (it defaults to {@code false}).
 */
@ConfigMapping(prefix = "babymonitor.motion")
public interface MotionConfig {

    /** Master switch. While false, no config is written and no process is started. */
    @WithDefault("false")
    boolean enabled();

    /** The Motion executable to run. */
    @WithDefault("/usr/bin/motion")
    String binary();

    /** Directory holding the generated configs, the template and the per-camera data. */
    @WithDefault("/home/bob/motion")
    String baseDir();

    /** Name of the base config that every generated file is built from. */
    @WithDefault("motion.template")
    String templateName();

    /** Name of the helper script Motion runs from its event hooks. */
    @WithDefault("motion-notify.sh")
    String notifyScriptName();

    /** Reconcile running processes with the database when the app starts. */
    @WithDefault("true")
    boolean reconcileOnStart();

    /** Webhook the event script posts recordings to. */
    @WithDefault("http://localhost:8080/api/webhooks/videoNotification")
    String webhookUrl();
}
