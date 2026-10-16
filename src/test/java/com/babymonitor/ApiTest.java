package com.babymonitor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.babymonitor.entity.Notification;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests against the running PostgreSQL database.
 *
 * <p>They rely on the rows seeded by {@code db/init.sql}: the
 * {@code systemName} setting, the four accounts
 * whose password is their own name, and the test notifications. The webhook
 * tests write to {@code babymonitor.notifications} and remove the row they
 * created in a {@code finally}, so the table does not grow on every run --
 * through {@link #deleteNotificationRow(long)}, because dismissing only hides a
 * notification for one account and deliberately leaves the row in place.
 */
@QuarkusTest
class ApiTest {

    private static final String AUDIO_WEBHOOK = "/api/webhooks/audioNotification";
    private static final String VIDEO_WEBHOOK = "/api/webhooks/videoNotification";

    /** Mantine's palette, as accepted by {@code PUT /api/users/me/theme-color}. */
    private static final String[] PALETTE = {
        "gray", "red", "pink", "grape", "violet", "indigo", "blue", "cyan",
        "teal", "green", "lime", "yellow", "orange",
    };

    /** Accepted values of promptSound on the UserDto and via {@code PUT /api/users/me/prompt-sound}. */
    private static final String[] PROMPT_SOUNDS = {
        "chime", "beep", "chirp", "ding", "doorbell", "soft", "alarm-loud",
    };

    private static String login(String name, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"" + name + "\",\"password\":\"" + password + "\"}")
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

    /** Reads the notification feed as a list of maps, newest first. */
    private static List<Map<String, Object>> feed(String token) {
        return given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/notifications")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    private static Map<String, Object> rowFor(List<Map<String, Object>> feed, String description) {
        return feed.stream()
                .filter(row -> description.equals(row.get("description")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no notification with description: " + description));
    }

    /** Whether a feed shows a notification, without failing when it does not. */
    private static boolean feedContains(List<Map<String, Object>> feed, String description) {
        return feed.stream().anyMatch(row -> description.equals(row.get("description")));
    }

    /** Dismisses a notification for one account. */
    private static void dismiss(String token, long id) {
        given()
                .header("Authorization", bearer(token))
                .when()
                .delete("/api/notifications/" + id)
                .then()
                .statusCode(204);
    }

    /** Posts an audio alert and returns the id of the row it created. */
    private static long postAudioWebhook(String description) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"description\":\"" + description + "\"}")
                .when()
                .post(AUDIO_WEBHOOK)
                .then()
                .statusCode(202)
                .extract()
                .jsonPath()
                .getLong("id");
    }

    /**
     * Removes a notification row outright. Dismissing cannot do this any more
     * (it only hides the notification for one account), so tests that create
     * rows clean up here. The dismissal rows go with it, by way of the foreign
     * key's {@code ON DELETE CASCADE}.
     */
    private static void deleteNotificationRow(long id) {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteById(id));
    }

    /** Whether the notification row is still stored at all. */
    private static boolean notificationRowExists(long id) {
        return QuarkusTransaction.requiringNew().call(() -> Notification.findById(id) != null);
    }

    /** Reads one camera's parameters as a list of maps, in the order the API returns them. */
    private static List<Map<String, Object>> cameraParameters(String token, int cameraNumber) {
        return given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/" + cameraNumber)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    private static Map<String, Object> parameterNamed(List<Map<String, Object>> parameters, String key) {
        return parameters.stream()
                .filter(row -> key.equals(row.get("key")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no parameter named: " + key));
    }

    @Test
    void openApiSchemaIsPublished() {
        given().when().get("/q/openapi").then().statusCode(200);
    }

    @Test
    void loginSucceedsForSeededUser() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Bob\",\"password\":\"Bob\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("name", equalTo("Bob"))
                .body("token", not(emptyString()))
                // The SPA applies both preferences straight from the login response.
                .body("theme", oneOf("light", "dark", "auto"))
                .body("themeColor", oneOf(PALETTE));
    }

    @Test
    void loginFailsWithWrongPassword() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Bob\",\"password\":\"not-bob\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("error", equalTo("Invalid Name or Password."));
    }

    @Test
    void loginFailsWithUnknownName() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Nobody\",\"password\":\"whatever\"}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("error", equalTo("Invalid Name or Password."));
    }

    @Test
    void protectedEndpointRejectsMissingToken() {
        given().when().get("/api/settings").then().statusCode(401);
    }

    @Test
    void streamingUrlIsNoLongerASetting() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/settings/streaming_url")
                .then()
                .statusCode(404)
                .body("error", equalTo("No such setting: streaming_url"));
    }

    @Test
    void settingsReturnSystemNameWhenAuthenticated() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/settings/systemName")
                .then()
                .statusCode(200)
                .body("value", equalTo("Cindy & Stephen's Baby Monitor"));
    }

    @Test
    void notificationFeedRequiresToken() {
        given().when().get("/api/notifications").then().statusCode(401);
    }

    @Test
    void audioWebhookPersistsAnAudioNotification() {
        String token = login("Bob", "Bob");
        long id = given()
                .contentType(ContentType.JSON)
                .body("{\"description\":\"Baby cried in the nursery\",\"eventTime\":\"2026-10-14T20:15:00Z\"}")
                .when()
                .post(AUDIO_WEBHOOK)
                .then()
                .statusCode(202)
                .body("status", equalTo("accepted"))
                .body("eventType", equalTo("Audio"))
                .extract()
                .jsonPath()
                .getLong("id");

        try {
            Map<String, Object> row = rowFor(feed(token), "Baby cried in the nursery");
            assertEquals("2026-10-14T20:15:00Z", row.get("eventTime"));
            assertEquals("Audio", row.get("eventType"));
            assertNull(row.get("videoLink"), "audio notifications carry no video link");
        } finally {
            deleteNotificationRow(id);
        }
    }

    @Test
    void audioWebhookIgnoresAVideoLink() {
        String token = login("Bob", "Bob");
        long id = given()
                .contentType(ContentType.JSON)
                .body("{\"description\":\"Baby cried again\","
                        + "\"videoLink\":\"http://cam.local/not-a-video-event.mp4\"}")
                .when()
                .post(AUDIO_WEBHOOK)
                .then()
                .statusCode(202)
                .body("eventType", equalTo("Audio"))
                .extract()
                .jsonPath()
                .getLong("id");

        try {
            Map<String, Object> row = rowFor(feed(token), "Baby cried again");
            assertEquals("Audio", row.get("eventType"));
            assertNull(row.get("videoLink"), "an audio event never keeps a video link");
        } finally {
            deleteNotificationRow(id);
        }
    }

    @Test
    void videoWebhookPersistsAVideoNotificationWithItsVideoLink() {
        String token = login("Bob", "Bob");
        long id = given()
                .contentType(ContentType.JSON)
                .body("{\"description\":\"Motion detected\",\"videoLink\":\"http://cam.local/clip.mp4\"}")
                .when()
                .post(VIDEO_WEBHOOK)
                .then()
                .statusCode(202)
                .body("status", equalTo("accepted"))
                .body("eventType", equalTo("Video"))
                .extract()
                .jsonPath()
                .getLong("id");

        try {
            Map<String, Object> row = rowFor(feed(token), "Motion detected");
            assertEquals("Video", row.get("eventType"));
            assertEquals("http://cam.local/clip.mp4", row.get("videoLink"));
            assertNull(row.get("severity"), "severity is no longer part of a notification");
            assertNull(row.get("read"), "read is no longer part of a notification");
        } finally {
            deleteNotificationRow(id);
        }
    }

    @Test
    void dismissRequiresAToken() {
        given().when().delete("/api/notifications/999999999").then().statusCode(401);
    }

    @Test
    void dismissRejectsAnUnknownNotification() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .delete("/api/notifications/999999999")
                .then()
                .statusCode(404)
                .body("error", equalTo("No such notification: 999999999"));
    }

    @Test
    void dismissHidesTheNotificationForThatAccountOnly() {
        String bob = login("Bob", "Bob");
        String cindy = login("Cindy", "Cindy");
        String description = "Baby laughed at the mobile";
        long id = postAudioWebhook(description);

        try {
            // Both accounts see the new event to begin with.
            assertTrue(feedContains(feed(bob), description));
            assertTrue(feedContains(feed(cindy), description));

            // Cindy dismisses it: hidden for her, still there for Bob.
            dismiss(cindy, id);
            assertFalse(feedContains(feed(cindy), description), "dismissed notification is hidden");
            assertTrue(feedContains(feed(bob), description), "another account still sees it");
            assertTrue(notificationRowExists(id), "the notification row itself is kept");

            // Dismissing twice is a no-op rather than an error.
            dismiss(cindy, id);
            assertFalse(feedContains(feed(cindy), description));

            // Bob's own dismissal is independent of hers.
            dismiss(bob, id);
            assertFalse(feedContains(feed(bob), description));
            assertTrue(notificationRowExists(id), "still on record for the household");
        } finally {
            deleteNotificationRow(id);
        }
    }

    @Test
    void webhookRequiresADescription() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post(AUDIO_WEBHOOK)
                .then()
                .statusCode(400)
                .body("error", equalTo("description is required"));
    }

    @Test
    void videoWebhookRequiresAVideoLink() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"description\":\"Motion detected\"}")
                .when()
                .post(VIDEO_WEBHOOK)
                .then()
                .statusCode(400)
                .body("error", equalTo("videoLink is required for videoNotification"));
    }

    @Test
    void userProfileRejectsMissingToken() {
        given().when().get("/api/users/me").then().statusCode(401);
    }

    @Test
    void userProfileReturnsDatabaseRecord() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("name", equalTo("Bob"))
                .body("id", notNullValue())
                .body("theme", oneOf("light", "dark", "auto"))
                .body("themeColor", oneOf(PALETTE))
                .body("promptSound", oneOf(PROMPT_SOUNDS));
    }

    @Test
    void themeRequiresAToken() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"theme\":\"dark\"}")
                .when()
                .put("/api/users/me/theme")
                .then()
                .statusCode(401);
    }

    @Test
    void themeIsStoredOnTheAccountAndPickedUpByTheNextLogin() {
        String token = login("Bob", "Bob");
        String original = given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .extract()
                .path("theme");

        try {
            // Values are normalised, so the exact casing does not matter.
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"theme\":\"DARK\"}")
                    .when()
                    .put("/api/users/me/theme")
                    .then()
                    .statusCode(200)
                    .body("theme", equalTo("dark"));

            // A new session sees the stored preference, in the login response
            // and on the profile.
            String freshToken = login("Bob", "Bob");
            given()
                    .header("Authorization", bearer(freshToken))
                    .when()
                    .get("/api/users/me")
                    .then()
                    .statusCode(200)
                    .body("theme", equalTo("dark"));
        } finally {
            // Put the row back the way the test found it.
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"theme\":\"" + original + "\"}")
                    .when()
                    .put("/api/users/me/theme")
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    void themeRejectsUnknownValues() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"theme\":\"neon\"}")
                .when()
                .put("/api/users/me/theme")
                .then()
                .statusCode(400)
                .body("error", equalTo("theme must be one of: light, dark, auto"));
    }

    @Test
    void themeRejectsAMissingValue() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .put("/api/users/me/theme")
                .then()
                .statusCode(400)
                .body("error", equalTo("theme must be one of: light, dark, auto"));
    }

    @Test
    void themeColorRequiresAToken() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"themeColor\":\"grape\"}")
                .when()
                .put("/api/users/me/theme-color")
                .then()
                .statusCode(401);
    }

    @Test
    void themeColorIsStoredOnTheAccountAndPickedUpByTheNextLogin() {
        String token = login("Bob", "Bob");
        String original = given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .extract()
                .path("themeColor");

        try {
            // Palette names are normalised, so the exact casing does not matter.
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"themeColor\":\"GRAPE\"}")
                    .when()
                    .put("/api/users/me/theme-color")
                    .then()
                    .statusCode(200)
                    .body("themeColor", equalTo("grape"));

            // A new session sees the stored color again.
            String freshToken = login("Bob", "Bob");
            given()
                    .header("Authorization", bearer(freshToken))
                    .when()
                    .get("/api/users/me")
                    .then()
                    .statusCode(200)
                    .body("themeColor", equalTo("grape"));
        } finally {
            // Put the row back the way the test found it.
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"themeColor\":\"" + original + "\"}")
                    .when()
                    .put("/api/users/me/theme-color")
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    void themeColorRejectsColorsOutsideThePalette() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"themeColor\":\"chartreuse\"}")
                .when()
                .put("/api/users/me/theme-color")
                .then()
                .statusCode(400)
                .body("error", equalTo("themeColor must be one of: " + String.join(", ", PALETTE)));
    }

    @Test
    void themeColorRejectsAMissingValue() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .put("/api/users/me/theme-color")
                .then()
                .statusCode(400)
                .body("error", equalTo("themeColor must be one of: " + String.join(", ", PALETTE)));
    }

    @Test
    void promptSoundRequiresAToken() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"promptSound\":\"alarm-loud\"}")
                .when()
                .put("/api/users/me/prompt-sound")
                .then()
                .statusCode(401);
    }

    @Test
    void promptSoundIsStoredAndPickedUpByTheNextLogin() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"promptSound\":\"alarm-loud\"}")
                .when()
                .put("/api/users/me/prompt-sound")
                .then()
                .statusCode(200)
                .body("promptSound", equalTo("alarm-loud"));

        String freshToken = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(freshToken))
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("promptSound", equalTo("alarm-loud"));

        // Restore default.
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"promptSound\":\"chime\"}")
                .when()
                .put("/api/users/me/prompt-sound")
                .then()
                .statusCode(200);
    }

    @Test
    void promptSoundRejectsUnknownValues() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"promptSound\":\"siren\"}")
                .when()
                .put("/api/users/me/prompt-sound")
                .then()
                .statusCode(400)
                .body("error", equalTo("promptSound must be one of: " + String.join(", ", PROMPT_SOUNDS)));
    }

    @Test
    void promptSoundAcceptsCaseInsensitiveValues() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"promptSound\":\"CHIME\"}")
                .when()
                .put("/api/users/me/prompt-sound")
                .then()
                .statusCode(200)
                .body("promptSound", equalTo("chime"));

        // Restore default.
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"promptSound\":\"chime\"}")
                .when()
                .put("/api/users/me/prompt-sound")
                .then()
                .statusCode(200);
    }

    // --- Environment time series ------------------------------------------

    @Test
    void environmentRequiresToken() {
        given().when().get("/api/environment").then().statusCode(401);
    }

    @Test
    void environmentReturnsNewestSamplesFirst() {
        String token = login("Bob", "Bob");
        List<Map<String, Object>> readings = given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/environment?limit=5")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");

        assertEquals(5, readings.size(), "the limit caps the rows returned");
        for (Map<String, Object> reading : readings) {
            assertNotNull(reading.get("eventTime"));
            assertNotNull(reading.get("temperature"));
            assertNotNull(reading.get("humidity"));
            assertNotNull(reading.get("brightness"));
        }

        List<Instant> times = readings.stream()
                .map(row -> Instant.parse((String) row.get("eventTime")))
                .toList();
        List<Instant> descending = new ArrayList<>(times);
        descending.sort(Comparator.reverseOrder());
        assertEquals(descending, times, "readings come back newest first");
    }

    @Test
    void environmentRejectsALimitOutsideTheAllowedRange() {
        String token = login("Bob", "Bob");
        for (String limit : List.of("0", "-1", "5000")) {
            given()
                    .header("Authorization", bearer(token))
                    .when()
                    .get("/api/environment?limit=" + limit)
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("limit must be between 1 and 2000"));
        }
    }

    // --- Camera parameters -------------------------------------------------

    @Test
    void camerasRequireToken() {
        given().when().get("/api/cameras").then().statusCode(401);
    }

    @Test
    void cameraReturnsItsParametersInNameOrder() {
        String token = login("Bob", "Bob");
        List<Map<String, Object>> parameters = cameraParameters(token, 1);

        assertFalse(parameters.isEmpty(), "camera 1 is seeded with parameters");
        for (Map<String, Object> parameter : parameters) {
            assertEquals(1, ((Number) parameter.get("cameraNumber")).intValue());
            assertEquals(Boolean.TRUE, parameter.get("enabled"), "camera 1 is seeded enabled");
        }

        List<String> keys = parameters.stream().map(row -> (String) row.get("key")).toList();
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        assertEquals(sorted, keys, "parameters are ordered by name");
        // The parameter names are Motion option names (USB cameras, not network).
        assertEquals("1280", parameterNamed(parameters, "width").get("value"));
        assertEquals("720", parameterNamed(parameters, "height").get("value"));
        assertNotNull(parameterNamed(parameters, "video_device").get("value"));
    }

    @Test
    void unknownCameraReturnsNotFound() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/999")
                .then()
                .statusCode(404)
                .body("error", equalTo("No such camera: 999"));
    }

    @Test
    void cameraParameterUpdateIsStoredAndRestored() {
        String token = login("Bob", "Bob");
        String original = (String) parameterNamed(cameraParameters(token, 1), "width").get("value");
        try {
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"value\":\"640\"}")
                    .when()
                    .put("/api/cameras/1/parameters/width")
                    .then()
                    .statusCode(200)
                    .body("value", equalTo("640"));

            assertEquals(
                    "640",
                    parameterNamed(cameraParameters(token, 1), "width").get("value"));
        } finally {
            // Put the parameter back the way the test found it.
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"value\":\"" + original + "\"}")
                    .when()
                    .put("/api/cameras/1/parameters/width")
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    void cameraParameterUpdateRejectsAMissingValue() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .put("/api/cameras/1/parameters/width")
                .then()
                .statusCode(400)
                .body("error", equalTo("A value is required"));
    }

    @Test
    void cameraEnabledToggleUpdatesEveryRowAndIsRestored() {
        String token = login("Bob", "Bob");
        boolean original = (Boolean) parameterNamed(cameraParameters(token, 2), "camera_name").get("enabled");
        try {
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"enabled\":true}")
                    .when()
                    .put("/api/cameras/2/enabled")
                    .then()
                    .statusCode(200);

            List<Map<String, Object>> parameters = cameraParameters(token, 2);
            assertFalse(parameters.isEmpty());
            for (Map<String, Object> parameter : parameters) {
                assertEquals(Boolean.TRUE, parameter.get("enabled"), "every row carries the flag");
            }
        } finally {
            // Put the camera back the way the test found it.
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"enabled\":" + original + "}")
                    .when()
                    .put("/api/cameras/2/enabled")
                    .then()
                    .statusCode(200);
        }
    }

    // --- Motion process ----------------------------------------------------

    @Test
    void motionStatusRequiresToken() {
        given().when().get("/api/cameras/1/motion").then().statusCode(401);
    }

    @Test
    void motionStatusForAnUnknownCameraIsNotFound() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/999/motion")
                .then()
                .statusCode(404);
    }

    @Test
    void motionReportsStoppedWhileTheFeatureIsDisabled() {
        String token = login("Bob", "Bob");
        // babymonitor.motion.enabled defaults to false, so nothing is driven and
        // no process is ever spawned by the tests.
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/1/motion")
                .then()
                .statusCode(200)
                .body("cameraNumber", equalTo(1))
                .body("managed", equalTo(false))
                .body("running", equalTo(false))
                .body("pid", equalTo(null))
                .body("configExists", equalTo(false));
    }

    @Test
    void motionApplyIsAcceptedButSpawnsNothingWhileDisabled() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cameras/1/motion/apply")
                .then()
                .statusCode(200)
                .body("managed", equalTo(false))
                .body("running", equalTo(false));
    }

    @Test
    void motionStopIsAcceptedAndIsANoOpWhileDisabled() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cameras/1/motion/stop")
                .then()
                .statusCode(200)
                .body("running", equalTo(false));
    }
}
