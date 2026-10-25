package com.babymonitor.environment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.babymonitor.entity.CameraLight;
import com.babymonitor.entity.CameraLightSample;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The camera light configuration/calibration API. */
@QuarkusTest
class CameraLightApiTest {

    @AfterEach
    void reset() {
        QuarkusTransaction.requiringNew().run(() -> {
            CameraLightSample.deleteAll();
            CameraLight row = CameraLight.current();
            row.cameraNumber = 1;
            row.exposureMs = 20;
            row.enabled = false;
            row.darkLuma = null;
            row.veryDimLuma = null;
            row.dimLuma = null;
            row.moderateLuma = null;
            row.brightLuma = null;
            row.veryBrightLuma = null;
            row.lastLuma = null;
            row.lastBand = null;
            row.lastLux = null;
            row.lastSampleAt = null;
            row.lastAlertedBand = null;
        });
    }

    @Test
    void requiresAToken() {
        given().when().get("/api/environment/light").then().statusCode(401);
        given()
                .contentType(ContentType.JSON)
                .body(validBody())
                .when()
                .put("/api/environment/light")
                .then()
                .statusCode(401);
    }

    @Test
    void returnsTheDefaults() {
        Map<String, Object> body = get(login());

        assertEquals(1, ((Number) body.get("cameraNumber")).intValue());
        assertEquals(20, ((Number) body.get("exposureMs")).intValue());
        assertEquals(false, body.get("enabled"));
        assertNull(body.get("darkLuma"));
        assertNull(body.get("lastBand"));
    }

    @Test
    void returnsTheHourlyCameraLightHistory() {
        Instant now = Instant.now();
        QuarkusTransaction.requiringNew().run(() -> {
            new CameraLightSample(now.minus(2, ChronoUnit.HOURS), 20.0, 3, 50).persist();
            new CameraLightSample(now.minus(1, ChronoUnit.HOURS), 30.0, 4, 300).persist();
        });

        given()
                .header("Authorization", bearer(login()))
                .when()
                .get("/api/environment/light/samples?hours=24")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("[0].band", is(3))
                .body("[1].band", is(4))
                .body("[0].hour", endsWith(":00:00Z"));
    }

    @Test
    void savesConfigurationAndCalibration() {
        String token = login();

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body(validBody())
                .when()
                .put("/api/environment/light")
                .then()
                .statusCode(200);

        Map<String, Object> body = get(token);
        assertEquals(1, ((Number) body.get("cameraNumber")).intValue());
        assertEquals(25, ((Number) body.get("exposureMs")).intValue());
        assertEquals(true, body.get("enabled"));
        assertEquals(10.0, number(body, "darkLuma"), 0.001);
        assertEquals(220.0, number(body, "veryBrightLuma"), 0.001);
    }

    @Test
    void rejectsInvalidConfiguration() {
        String token = login();

        assertRejected(token, change("cameraNumber", 9), "No such camera");
        assertRejected(token, change("exposureMs", 0), "exposureMs must be");
        assertRejected(token, change("exposureMs", 151), "exposureMs must be");
        assertRejected(token, change("darkLuma", null), "All six calibration lumas");
        assertRejected(token, change("brightLuma", 5), "must increase");

        Map<String, Object> uncalibrated = new HashMap<>();
        uncalibrated.put("cameraNumber", 1);
        uncalibrated.put("exposureMs", 20);
        uncalibrated.put("enabled", true);
        assertRejected(token, uncalibrated, "Calibrate all six bands");

        Map<String, Object> missing = new HashMap<>(validBody());
        missing.remove("enabled");
        assertRejected(token, missing, "cameraNumber, exposureMs and enabled are required");
    }

    private static void assertRejected(String token, Map<String, Object> body, String message) {
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put("/api/environment/light")
                .then()
                .statusCode(400)
                .body("error", containsString(message));
    }

    private static Map<String, Object> get(String token) {
        return given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/environment/light")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    private static double number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).doubleValue();
    }

    private static Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("cameraNumber", 1);
        body.put("exposureMs", 25);
        body.put("enabled", true);
        body.put("darkLuma", 10);
        body.put("veryDimLuma", 25);
        body.put("dimLuma", 45);
        body.put("moderateLuma", 90);
        body.put("brightLuma", 160);
        body.put("veryBrightLuma", 220);
        return body;
    }

    private static Map<String, Object> change(String key, Object value) {
        Map<String, Object> body = new HashMap<>(validBody());
        body.put(key, value);
        return body;
    }

    private static String login() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Bob\",\"password\":\"Bob\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
