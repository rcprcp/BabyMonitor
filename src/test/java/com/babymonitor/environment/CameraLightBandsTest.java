package com.babymonitor.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Calibration, band cut points and the proxy lux table. */
class CameraLightBandsTest {

    private static final CameraLightBands BANDS = new CameraLightBands(10, 25, 45, 90, 160, 220);

    @Test
    void mapsLumaToTheSixBands() {
        assertEquals("dark", BANDS.bandFor(0));
        assertEquals("dark", BANDS.bandFor(17.4));
        assertEquals("very-dim", BANDS.bandFor(17.5));
        assertEquals("very-dim", BANDS.bandFor(30));
        assertEquals("dim", BANDS.bandFor(40));
        assertEquals("moderate", BANDS.bandFor(100));
        assertEquals("bright", BANDS.bandFor(150));
        assertEquals("very-bright", BANDS.bandFor(200));
        assertEquals("very-bright", BANDS.bandFor(255));
    }

    @Test
    void proxyLuxAndLabelsFollowTheBandOrder() {
        assertEquals(1, CameraLightBands.proxyLux("dark"));
        assertEquals(10, CameraLightBands.proxyLux("very-dim"));
        assertEquals(50, CameraLightBands.proxyLux("dim"));
        assertEquals(300, CameraLightBands.proxyLux("moderate"));
        assertEquals(1000, CameraLightBands.proxyLux("bright"));
        assertEquals(3000, CameraLightBands.proxyLux("very-bright"));
        assertEquals(0, CameraLightBands.proxyLux("unknown"));
        assertEquals("Very dim", CameraLightBands.label("very-dim"));
        assertEquals("Very bright", CameraLightBands.label("very-bright"));
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
