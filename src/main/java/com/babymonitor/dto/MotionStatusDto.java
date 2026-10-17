package com.babymonitor.dto;

/**
 * The state of one camera's Motion process, as returned to the SPA.
 *
 * @param cameraNumber the camera
 * @param running      whether a Motion process is running for this camera
 * @param pid          its process id, or {@code null} when it is stopped
 * @param configFile   the generated config file the process reads
 * @param configExists whether that file has been written yet
 */
public record MotionStatusDto(
        int cameraNumber,
        boolean running,
        Long pid,
        String configFile,
        boolean configExists) {
}
