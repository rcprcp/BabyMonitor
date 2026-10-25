package com.babymonitor.environment;

import com.babymonitor.dto.CameraLightDto;
import com.babymonitor.entity.CameraLight;

/**
 * The luma calibration that maps a probe to one of the six
 * {@link BrightnessBand}s.
 *
 * <p>Calibration captures one luma per band. The cut point between two bands is
 * the midpoint of their captured lumas, so the bands follow the camera's own
 * response. The resulting band number is stored; the captured lumas only affect
 * how future probes are mapped, never the meaning of an already stored band.</p>
 */
public record CameraLightBands(
        double dark,
        double veryDim,
        double dim,
        double moderate,
        double bright,
        double veryBright) {

    public static CameraLightBands from(CameraLight row) {
        return from(
                row.darkLuma,
                row.veryDimLuma,
                row.dimLuma,
                row.moderateLuma,
                row.brightLuma,
                row.veryBrightLuma);
    }

    public static CameraLightBands from(CameraLightDto dto) {
        return from(
                dto.darkLuma(),
                dto.veryDimLuma(),
                dto.dimLuma(),
                dto.moderateLuma(),
                dto.brightLuma(),
                dto.veryBrightLuma());
    }

    private static CameraLightBands from(
            Double dark,
            Double veryDim,
            Double dim,
            Double moderate,
            Double bright,
            Double veryBright) {
        if (dark == null
                || veryDim == null
                || dim == null
                || moderate == null
                || bright == null
                || veryBright == null) {
            return null;
        }
        return new CameraLightBands(dark, veryDim, dim, moderate, bright, veryBright);
    }

    /** Whether the six calibration points are present and strictly increasing. */
    public boolean calibrated() {
        return dark < veryDim
                && veryDim < dim
                && dim < moderate
                && moderate < bright
                && bright < veryBright;
    }

    /**
     * Maps a luma to a band using the captured calibration points, or
     * {@code null} when the sensor is not calibrated.
     */
    public BrightnessBand bandFor(double luma) {
        if (!calibrated()) {
            return null;
        }
        double[] values = {dark, veryDim, dim, moderate, bright, veryBright};
        BrightnessBand[] bands = BrightnessBand.values();
        for (int i = 0; i < bands.length - 1; i++) {
            if (luma < (values[i] + values[i + 1]) / 2) {
                return bands[i];
            }
        }
        return bands[bands.length - 1];
    }

    /**
     * Validation message for a set of calibration points, or {@code null} when
     * they are usable.
     */
    public static String validate(
            Double dark,
            Double veryDim,
            Double dim,
            Double moderate,
            Double bright,
            Double veryBright) {
        Double[] values = {dark, veryDim, dim, moderate, bright, veryBright};
        for (Double value : values) {
            if (value == null || !Double.isFinite(value) || value < 0 || value > 255) {
                return "All six calibration lumas are required, between 0 and 255";
            }
        }
        for (int i = 1; i < values.length; i++) {
            if (values[i] <= values[i - 1]) {
                return "Calibration lumas must increase from dark to very bright";
            }
        }
        return null;
    }
}
