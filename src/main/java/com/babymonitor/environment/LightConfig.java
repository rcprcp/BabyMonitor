package com.babymonitor.environment;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** External tools and timeout for the UVC camera light probe. */
@ConfigMapping(prefix = "babymonitor.light")
public interface LightConfig {

    /** The v4l2 control tool used to switch exposure modes. */
    @WithDefault("/usr/bin/v4l2-ctl")
    String v4l2Ctl();

    /** The ffmpeg binary used to read one frame and its luma. */
    @WithDefault("/usr/bin/ffmpeg")
    String ffmpeg();

    /** How long a probe command may run before it is killed. */
    @WithDefault("10")
    int timeoutSeconds();

    /** How often the scheduled probe runs. */
    @WithDefault("60s")
    String checkInterval();
}
