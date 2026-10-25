package com.babymonitor.environment;

/**
 * The six brightness bands, numbered 1..6.
 *
 * <p>The number is the value stored in the database and returned by the API.
 * The captured luma calibration only decides which number a new luma maps to;
 * changing a captured luma therefore never renumbers bands that are already
 * stored.</p>
 */
public enum BrightnessBand {
    DARK(1, "dark", "Dark", 1),
    VERY_DIM(2, "very-dim", "Very dim", 10),
    DIM(3, "dim", "Dim", 50),
    MODERATE(4, "moderate", "Moderate", 300),
    BRIGHT(5, "bright", "Bright", 1000),
    VERY_BRIGHT(6, "very-bright", "Very bright", 3000);

    private final int number;
    private final String key;
    private final String label;
    private final int proxyLux;

    BrightnessBand(int number, String key, String label, int proxyLux) {
        this.number = number;
        this.key = key;
        this.label = label;
        this.proxyLux = proxyLux;
    }

    /** The stable stored value, 1 (dark) through 6 (very bright). */
    public int number() {
        return number;
    }

    /** The lower-case key used by the UI's band colours. */
    public String key() {
        return key;
    }

    /** The human-readable label. */
    public String label() {
        return label;
    }

    /** Representative lux for this band, used only by the brightness alert. */
    public int proxyLux() {
        return proxyLux;
    }

    /** The band for a stored number, or {@code null} when it is not 1..6. */
    public static BrightnessBand fromNumber(Integer number) {
        if (number == null) {
            return null;
        }
        for (BrightnessBand band : values()) {
            if (band.number == number) {
                return band;
            }
        }
        return null;
    }

    /** The label for a stored number, falling back to a readable placeholder. */
    public static String labelForNumber(Integer number) {
        BrightnessBand band = fromNumber(number);
        return band == null ? "Band " + number : band.label;
    }
}
