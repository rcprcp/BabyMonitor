package com.babymonitor.environment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.babymonitor.entity.EnvironmentThresholds;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Environment page's high/low bounds API. */
@QuarkusTest
class EnvironmentThresholdsApiTest {

    @AfterEach
    void reset() {
        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentThresholds row = EnvironmentThresholds.current();
            row.humidityLow = BigDecimal.valueOf(30);
            row.humidityHigh = BigDecimal.valueOf(75);
            row.temperatureLow = BigDecimal.valueOf(66);
            row.temperatureHigh = BigDecimal.valueOf(76);
            row.brightnessLow = 200;
            row.brightnessHigh = 2000;
        });
    }

    @Test
    void requiresAToken() {
        given().when().get("/api/environment/thresholds").then().statusCode(401);
        given()
                .contentType(ContentType.JSON)
                .body(defaults())
                .when()
                .put("/api/environment/thresholds")
                .then()
                .statusCode(401);
    }

    @Test
    void returnsTheDefaults() {
        Map<String, Object> body = get(login());

        assertEquals(30.0, number(body, "humidityLow"), 0.001);
        assertEquals(75.0, number(body, "humidityHigh"), 0.001);
        assertEquals(66.0, number(body, "temperatureLow"), 0.001);
        assertEquals(76.0, number(body, "temperatureHigh"), 0.001);
        assertEquals(200.0, number(body, "brightnessLow"), 0.001);
        assertEquals(2000.0, number(body, "brightnessHigh"), 0.001);
    }

    @Test
    void updatesTheBounds() {
        String token = login();
        Map<String, Object> update = Map.of(
                "humidityLow", 40,
                "humidityHigh", 60,
                "temperatureLow", 68,
                "temperatureHigh", 74,
                "brightnessLow", 300,
                "brightnessHigh", 1500);

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body(update)
                .when()
                .put("/api/environment/thresholds")
                .then()
                .statusCode(200);

        Map<String, Object> body = get(token);
        assertEquals(40.0, number(body, "humidityLow"), 0.001);
        assertEquals(60.0, number(body, "humidityHigh"), 0.001);
        assertEquals(68.0, number(body, "temperatureLow"), 0.001);
        assertEquals(74.0, number(body, "temperatureHigh"), 0.001);
        assertEquals(300.0, number(body, "brightnessLow"), 0.001);
        assertEquals(1500.0, number(body, "brightnessHigh"), 0.001);
    }

    @Test
    void rejectsInvalidBounds() {
        String token = login();

        assertRejected(token, change("humidityLow", 80), "Humidity must be");
        assertRejected(token, change("temperatureLow", 20), "Temperature must be");
        assertRejected(token, change("brightnessHigh", 4000), "Brightness must be");

        Map<String, Object> missing = new HashMap<>(defaults());
        missing.remove("brightnessHigh");
        assertRejected(token, missing, "All six bounds are required");
    }

    private static void assertRejected(String token, Map<String, Object> body, String message) {
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .put("/api/environment/thresholds")
                .then()
                .statusCode(400)
                .body("error", containsString(message));
    }

    private static Map<String, Object> get(String token) {
        return given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/environment/thresholds")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
    }

    private static double number(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).doubleValue();
    }

    private static Map<String, Object> defaults() {
        return Map.of(
                "humidityLow", 30,
                "humidityHigh", 75,
                "temperatureLow", 66,
                "temperatureHigh", 76,
                "brightnessLow", 200,
                "brightnessHigh", 2000);
    }

    private static Map<String, Object> change(String key, Object value) {
        Map<String, Object> body = new HashMap<>(defaults());
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
