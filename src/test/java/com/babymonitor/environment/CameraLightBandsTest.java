package com.babymonitor.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Calibration, band mapping and the stable band numbers. */
class CameraLightBandsTest {

    private static final CameraLightBands BANDS = new CameraLightBands(10, 25, 45, 90, 160, 220);

    @Test
    void mapsLumaToTheSixBands() {
        assertEquals(BrightnessBand.DARK, BANDS.bandFor(0));
        assertEquals(BrightnessBand.DARK, BANDS.bandFor(17.4));
        assertEquals(BrightnessBand.VERY_DIM, BANDS.bandFor(17.5));
        assertEquals(BrightnessBand.VERY_DIM, BANDS.bandFor(30));
        assertEquals(BrightnessBand.DIM, BANDS.bandFor(40));
        assertEquals(BrightnessBand.MODERATE, BANDS.bandFor(100));
        assertEquals(BrightnessBand.BRIGHT, BANDS.bandFor(150));
        assertEquals(BrightnessBand.VERY_BRIGHT, BANDS.bandFor(200));
        assertEquals(BrightnessBand.VERY_BRIGHT, BANDS.bandFor(255));
    }

    @Test
    void bandNumbersLabelsAndProxyLuxAreStable() {
        assertEquals(1, BrightnessBand.DARK.number());
        assertEquals(2, BrightnessBand.VERY_DIM.number());
        assertEquals(3, BrightnessBand.DIM.number());
        assertEquals(4, BrightnessBand.MODERATE.number());
        assertEquals(5, BrightnessBand.BRIGHT.number());
        assertEquals(6, BrightnessBand.VERY_BRIGHT.number());

        assertEquals(1, BrightnessBand.DARK.proxyLux());
        assertEquals(10, BrightnessBand.VERY_DIM.proxyLux());
        assertEquals(50, BrightnessBand.DIM.proxyLux());
        assertEquals(300, BrightnessBand.MODERATE.proxyLux());
        assertEquals(1000, BrightnessBand.BRIGHT.proxyLux());
        assertEquals(3000, BrightnessBand.VERY_BRIGHT.proxyLux());

        assertEquals("Very dim", BrightnessBand.VERY_DIM.label());
        assertEquals(BrightnessBand.MODERATE, BrightnessBand.fromNumber(4));
        assertEquals("Band 9", BrightnessBand.labelForNumber(9));
        assertNull(BrightnessBand.fromNumber(9));
    }

    @Test
    void validationRequiresSixIncreasingPointsInsideTheLumaRange() {
        assertNull(CameraLightBands.validate(10.0, 25.0, 45.0, 90.0, 160.0, 220.0));
        assertNotNull(CameraLightBands.validate(null, 25.0, 45.0, 90.0, 160.0, 220.0));
        assertNotNull(CameraLightBands.validate(10.0, 25.0, 45.0, 90.0, 160.0, 300.0));
        assertNotNull(CameraLightBands.validate(10.0, 10.0, 45.0, 90.0, 160.0, 220.0));
        assertTrue(CameraLightBands.validate(10.0, 5.0, 45.0, 90.0, 160.0, 220.0)
                .contains("increase"));
    }

    @Test
    void anUncalibratedSetHasNoBand() {
        CameraLightBands flat = new CameraLightBands(10, 10, 20, 30, 40, 50);
        assertFalse(flat.calibrated());
        assertNull(flat.bandFor(20));
    }
}
