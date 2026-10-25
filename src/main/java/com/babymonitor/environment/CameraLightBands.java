package com.babymonitor.environment;

import com.babymonitor.dto.CameraLightDto;
import com.babymonitor.entity.CameraLight;
import java.util.List;

/**
 * The six brightness bands and the luma calibration that separates them.
 *
 * <p>Calibration captures one luma per band. The cut point between two bands is
 * the midpoint of their captured lumas, so the bands follow the camera's own
 * response instead of a fixed threshold table. The representative lux is only a
 * proxy for the environment brightness path; it is not a calibrated lux value.</p>
 */
public record CameraLightBands(
        double dark,
        double veryDim,
        double dim,
        double moderate,
        double bright,
        double veryBright) {

    public static final List<String> KEYS =
            List.of("dark", "very-dim", "dim", "moderate", "bright", "very-bright");

    public static final List<String> LABELS =
            List.of("Dark", "Very dim", "Dim", "Moderate", "Bright", "Very bright");

    /** Representative lux for each band, used as the environment brightness proxy. */
    public static final List<Integer> PROXY_LUX = List.of(1, 10, 50, 300, 1000, 3000);

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

    /** The band for a luma, or {@code null} when the sensor is not calibrated. */
    public String bandFor(double luma) {
        if (!calibrated()) {
            return null;
        }
        double[] values = {dark, veryDim, dim, moderate, bright, veryBright};
        for (int i = 0; i < KEYS.size() - 1; i++) {
            if (luma < (values[i] + values[i + 1]) / 2) {
                return KEYS.get(i);
            }
        }
        return KEYS.get(KEYS.size() - 1);
    }

    /** The representative lux for a band key, or 0 for an unknown band. */
    public static int proxyLux(String band) {
        int index = KEYS.indexOf(band);
        return index < 0 ? 0 : PROXY_LUX.get(index);
    }

    /** A human-readable label for a band key. */
    public static String label(String band) {
        int index = KEYS.indexOf(band);
        return index < 0 ? band : LABELS.get(index);
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
