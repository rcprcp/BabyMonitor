package com.babymonitor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.audio.AudioAlertSettings;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The Audio Notification Config page's API. */
@QuarkusTest
class AudioAlertConfigApiTest {

    @Inject
    AudioAlertSettings settings;

    @AfterEach
    void reset() {
        settings.resetToDefault();
    }

    @Test
    void requiresAToken() {
        given().when().get("/api/audio/alert-classes").then().statusCode(401);
        given()
                .contentType(ContentType.JSON)
                .body("{\"enabled\":true}")
                .when()
                .put("/api/audio/alert-classes/19")
                .then()
                .statusCode(401);
        given().contentType(ContentType.JSON).when().post("/api/audio/alert-classes/reset").then().statusCode(401);
    }

    @Test
    void listsEveryClassWithItsGroupAndSwitch() {
        String token = login();

        List<Map<String, Object>> groups = given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/audio/alert-classes")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("groups");
        List<Map<String, Object>> classes = given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/audio/alert-classes")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("classes");

        assertEquals(6, groups.size());
        assertEquals(521, classes.size());

        Map<String, Object> crying = classAt(classes, 19);
        assertEquals("Crying, sobbing", crying.get("name"));
        assertEquals(1, ((Number) crying.get("groupId")).intValue());
        assertEquals(Boolean.TRUE, crying.get("enabled"));

        Map<String, Object> drum = classAt(classes, 159);
        assertEquals("Drum", drum.get("name"));
        assertEquals(2, ((Number) drum.get("groupId")).intValue());
        assertEquals(Boolean.FALSE, drum.get("enabled"));
    }

    @Test
    void togglingOneClassPersists() {
        String token = login();

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"enabled\":true}")
                .when()
                .put("/api/audio/alert-classes/159")
                .then()
                .statusCode(204);
        assertTrue(settings.enabledIndices().contains(159));

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"enabled\":false}")
                .when()
                .put("/api/audio/alert-classes/159")
                .then()
                .statusCode(204);
        assertFalse(settings.enabledIndices().contains(159));
    }

    @Test
    void aBulkUpdateAndResetReplaceTheSet() {
        String token = login();

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"indices\":[11,19,20,21]}")
                .when()
                .put("/api/audio/alert-classes")
                .then()
                .statusCode(204);
        assertEquals(Set.of(11, 19, 20, 21), settings.enabledIndices());

        given()
                .header("Authorization", bearer(token))
                .when()
                .post("/api/audio/alert-classes/reset")
                .then()
                .statusCode(200)
                .body("classes.find { it.index == 0 }.enabled", equalTo(true));
        assertEquals(defaults(), settings.enabledIndices());
    }

    @Test
    void rejectsAMissingFlagAndAnUnknownClass() {
        String token = login();

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .put("/api/audio/alert-classes/19")
                .then()
                .statusCode(400)
                .body("error", equalTo("An enabled flag is required"));

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"enabled\":true}")
                .when()
                .put("/api/audio/alert-classes/999")
                .then()
                .statusCode(404);
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

    private static Map<String, Object> classAt(List<Map<String, Object>> classes, int index) {
        return classes.stream()
                .filter(row -> ((Number) row.get("index")).intValue() == index)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no class at index " + index));
    }

    private static Set<Integer> defaults() {
        return IntStream.rangeClosed(0, 61).boxed().collect(Collectors.toSet());
    }
}
