package com.babymonitor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
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

import com.babymonitor.entity.Camera;
import com.babymonitor.entity.Notification;
import com.babymonitor.entity.NotificationDismissal;
import com.babymonitor.entity.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.nio.file.Files;
import java.nio.file.Path;
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
@QuarkusTestResource(MotionTestResource.class)
@QuarkusTestResource(AudioTestResource.class)
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

    /** Removes one account's dismissal rows, for test clean-up. */
    private static void clearDismissals(String name) {
        Long userId = QuarkusTransaction.requiringNew().call(() -> {
            User user = User.findByName(name);
            return user == null ? null : user.id;
        });
        if (userId != null) {
            QuarkusTransaction.requiringNew()
                    .run(() -> NotificationDismissal.delete("userId = ?1", userId));
        }
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

    /**
     * Inserts a throwaway camera parameter. Delete tests use one instead of a
     * seeded row, so a failed request cannot leave the camera missing a
     * parameter it needs.
     */
    private static void insertCameraParameter(int cameraNumber, String key, String value) {
        QuarkusTransaction.requiringNew().run(() -> {
            Camera probe = new Camera();
            probe.cameraNumber = cameraNumber;
            probe.paramKey = key;
            probe.value = value;
            probe.enabled = false;
            probe.persist();
        });
    }

    /** Whether the camera parameter is still stored at all. */
    private static boolean cameraParameterRowExists(int cameraNumber, String key) {
        return QuarkusTransaction.requiringNew().call(() -> Camera.findOne(cameraNumber, key) != null);
    }

    /** Removes a camera parameter row whatever its state, for test clean-up. */
    private static void deleteCameraParameterRow(int cameraNumber, String key) {
        QuarkusTransaction.requiringNew()
                .run(() -> Camera.delete("cameraNumber = ?1 and paramKey = ?2", cameraNumber, key));
    }

    @Test
    void openApiSchemaIsPublished() {
        given().when().get("/q/openapi").then().statusCode(200);
    }

    @Test
    void healthEndpointReportsUp() {
        given()
                .when()
                .get("/q/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
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
    void onboardingSettingTellsTheWizardWhetherToRun() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/settings/onboardingComplete")
                .then()
                .statusCode(200)
                .body("tag", equalTo("onboardingComplete"))
                .body("value", notNullValue());
    }

    @Test
    void videoDevicesRequireAToken() {
        given().when().get("/api/video-devices").then().statusCode(401);
    }

    @Test
    void videoDevicesAreListedForTheWizard() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/video-devices")
                .then()
                .statusCode(200);
    }

    @Test
    void publicSystemNameIsAvailableWithoutAToken() {
        given()
                .when()
                .get("/api/public/system-name")
                .then()
                .statusCode(200)
                .body("name", equalTo("Cindy & Stephen's Baby Monitor"));
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
    void dismissAllRequiresAToken() {
        given().when().delete("/api/notifications").then().statusCode(401);
    }

    @Test
    void dismissAllClearsOnlyTheCallingAccountsFeed() {
        String cindy = login("Cindy", "Cindy");
        String bob = login("Bob", "Bob");
        try {
            assertFalse(feed(cindy).isEmpty(), "Cindy starts with the seeded feed");
            assertFalse(feed(bob).isEmpty(), "Bob starts with the seeded feed");

            given()
                    .header("Authorization", bearer(cindy))
                    .when()
                    .delete("/api/notifications")
                    .then()
                    .statusCode(204);

            assertTrue(feed(cindy).isEmpty(), "Cindy's feed is empty after Dismiss All");
            assertFalse(feed(bob).isEmpty(), "Bob's feed is untouched");

            // Idempotent: pressing it again is not an error.
            given()
                    .header("Authorization", bearer(cindy))
                    .when()
                    .delete("/api/notifications")
                    .then()
                    .statusCode(204);

            // A notification recorded afterwards is still visible to Cindy.
            long id = postAudioWebhook("After Dismiss All");
            try {
                assertTrue(
                        feedContains(feed(cindy), "After Dismiss All"),
                        "new events still arrive after Dismiss All");
            } finally {
                deleteNotificationRow(id);
            }
        } finally {
            clearDismissals("Cindy");
        }
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

        // Every row carries the camera number and the camera's own enabled flag.
        // Read the flag rather than assuming the seeded FALSE: the Camera pages
        // start and stop cameras (and other tests do too), so it is live state.
        Object enabled = parameters.get(0).get("enabled");
        assertNotNull(enabled, "every parameter carries an enabled flag");
        for (Map<String, Object> parameter : parameters) {
            assertEquals(1, ((Number) parameter.get("cameraNumber")).intValue());
            assertEquals(
                    enabled,
                    parameter.get("enabled"),
                    "every row shares the camera's enabled flag");
        }

        List<String> keys = parameters.stream().map(row -> (String) row.get("key")).toList();
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        assertEquals(sorted, keys, "parameters are ordered by name");

        // The parameter names are Motion option names (USB cameras, not network).
        // Their values are user-editable from the Camera pages, so only require
        // that the required options are present and carry a value -- pinning the
        // seeded 1280x720 makes the suite fail against any database a user has
        // edited (the rows are only seeded while missing).
        for (String key : List.of("width", "height", "video_device")) {
            assertFalse(
                    ((String) parameterNamed(parameters, key).get("value")).isBlank(),
                    key + " carries a value");
        }
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
    void cameraParameterCanBeAddedAndRemoved() {
        String token = login("Bob", "Bob");
        // A new row inherits the camera's own enabled flag, and other tests move
        // that flag around, so read it rather than assuming a value.
        boolean cameraEnabled =
                (Boolean) parameterNamed(cameraParameters(token, 1), "camera_name").get("enabled");
        String key = "zz_agent_add_probe";
        try {
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"key\":\"" + key + "\",\"value\":\"1\"}")
                    .when()
                    .post("/api/cameras/1/parameters")
                    .then()
                    .statusCode(201)
                    .body("key", equalTo(key))
                    .body("value", equalTo("1"))
                    .body("cameraNumber", equalTo(1))
                    .body("enabled", equalTo(cameraEnabled));

            assertTrue(
                    cameraParameters(token, 1).stream().anyMatch(row -> key.equals(row.get("key"))),
                    "the new row is listed");

            given()
                    .header("Authorization", bearer(token))
                    .when()
                    .delete("/api/cameras/1/parameters/" + key)
                    .then()
                    .statusCode(204);

            assertFalse(
                    cameraParameters(token, 1).stream().anyMatch(row -> key.equals(row.get("key"))),
                    "the removed row is gone");
        } finally {
            deleteCameraParameterRow(1, key);
        }
    }

    @Test
    void cameraParameterCreateRejectsABlankName() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"key\":\"   \",\"value\":\"1\"}")
                .when()
                .post("/api/cameras/1/parameters")
                .then()
                .statusCode(400)
                .body("error", equalTo("A parameter name is required"));
    }

    @Test
    void cameraParameterCreateRejectsABlankValue() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"key\":\"zz_agent_add_probe\",\"value\":\"  \"}")
                .when()
                .post("/api/cameras/1/parameters")
                .then()
                .statusCode(400)
                .body("error", equalTo("A value is required"));
    }

    @Test
    void cameraParameterCreateRejectsADuplicate() {
        String token = login("Bob", "Bob");
        String original = (String) parameterNamed(cameraParameters(token, 1), "width").get("value");

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"key\":\"width\",\"value\":\"640\"}")
                .when()
                .post("/api/cameras/1/parameters")
                .then()
                .statusCode(409);

        // The refused create must leave the existing row's value alone.
        assertEquals(original, parameterNamed(cameraParameters(token, 1), "width").get("value"));
    }

    @Test
    void cameraParameterCreateRequiresToken() {
        // A name no camera defines, so a missing guard could not create anything.
        given()
                .contentType(ContentType.JSON)
                .body("{\"key\":\"zz_agent_unauth\",\"value\":\"1\"}")
                .when()
                .post("/api/cameras/1/parameters")
                .then()
                .statusCode(401);
        assertFalse(cameraParameterRowExists(1, "zz_agent_unauth"), "nothing was created");
    }

    @Test
    void cameraParameterDeleteRemovesTheRow() {
        String token = login("Bob", "Bob");
        String key = "zz_agent_delete_probe";
        insertCameraParameter(1, key, "throwaway");
        try {
            given()
                    .header("Authorization", bearer(token))
                    .when()
                    .delete("/api/cameras/1/parameters/" + key)
                    .then()
                    .statusCode(204);

            List<String> keys = cameraParameters(token, 1).stream()
                    .map(row -> (String) row.get("key"))
                    .toList();
            assertFalse(keys.contains(key), "the deleted parameter is no longer listed");
            assertFalse(cameraParameterRowExists(1, key), "and its row is gone from the database");
        } finally {
            deleteCameraParameterRow(1, key);
        }
    }

    @Test
    void cameraParameterDeleteIsNotFoundForAnUnknownParameter() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .delete("/api/cameras/1/parameters/no_such_parameter")
                .then()
                .statusCode(404)
                .body("error", equalTo("No such parameter: no_such_parameter on camera 1"));
    }

    @Test
    void cameraParameterDeleteRequiresToken() {
        // A name no camera defines, so a missing guard could not delete anything.
        given().when().delete("/api/cameras/1/parameters/no_such_parameter").then().statusCode(401);
    }

    @Test
    void applyToggleBehaviour() {
        String token = login("Bob", "Bob");
        boolean original = (Boolean) parameterNamed(cameraParameters(token, 2), "camera_name").get("enabled");
        try {
            Map<String, Object> status = applyMotion(token, 2);
            assertTrue((Boolean) status.get("running"));
            List<Map<String, Object>> parameters = cameraParameters(token, 2);
            for (Map<String, Object> parameter : parameters) {
                assertEquals(Boolean.TRUE, parameter.get("enabled"));
            }
        } finally {
            stopMotion(token, 2);
            if (original) {
                applyMotion(token, 2);
            } else {
                stopMotion(token, 2);
            }
        }
    }

    // --- Camera audio ------------------------------------------------------

    @Test
    void cameraAudioStartAndStop() {
        String token = login("Bob", "Bob");
        // Begin from a known state: earlier tests may have left audio running.
        stopCameraAudio(token, 1);
        try {
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .when()
                    .post("/api/cameras/1/audio/start")
                    .then()
                    .statusCode(200)
                    .body("enabled", equalTo(true))
                    .body("running", equalTo(true));

            given()
                    .header("Authorization", bearer(token))
                    .when()
                    .get("/api/cameras/1/audio")
                    .then()
                    .statusCode(200)
                    .body("cameraNumber", equalTo(1))
                    .body("enabled", equalTo(true))
                    .body("running", equalTo(true));
        } finally {
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .when()
                    .post("/api/cameras/1/audio/stop")
                    .then()
                    .statusCode(200)
                    .body("enabled", equalTo(false))
                    .body("running", equalTo(false));
        }
    }

    @Test
    void cameraAudioRequiresToken() {
        given().contentType(ContentType.JSON).when().post("/api/cameras/1/audio/start").then().statusCode(401);
        given().contentType(ContentType.JSON).when().post("/api/cameras/1/audio/stop").then().statusCode(401);
    }

    @Test
    void cameraAudioForAnUnknownCameraIsNotFound() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cameras/999/audio/start")
                .then()
                .statusCode(404)
                .body("error", equalTo("No such camera: 999"));
    }

    @Test
    void startingACameraStartsWantedAudioAndStoppingKeepsThePreference() {
        String token = login("Bob", "Bob");
        try {
            // "Listen for cries" on: the camera brings the capture up with it.
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .when()
                    .post("/api/cameras/1/audio/start")
                    .then()
                    .statusCode(200);
            applyMotion(token, 1);
            given()
                    .header("Authorization", bearer(token))
                    .when()
                    .get("/api/cameras/1/audio")
                    .then()
                    .statusCode(200)
                    .body("enabled", equalTo(true))
                    .body("running", equalTo(true));
        } finally {
            stopMotion(token, 1);
        }
        // Stopping the camera stops the capture but keeps the preference, so
        // the next start listens again.
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/1/audio")
                .then()
                .statusCode(200)
                .body("enabled", equalTo(true))
                .body("running", equalTo(false));
    }

    @Test
    void startingACameraWithAudioOffLeavesItSilent() {
        String token = login("Bob", "Bob");
        stopCameraAudio(token, 1);
        try {
            applyMotion(token, 1);
            given()
                    .header("Authorization", bearer(token))
                    .when()
                    .get("/api/cameras/1/audio")
                    .then()
                    .statusCode(200)
                    .body("enabled", equalTo(false))
                    .body("running", equalTo(false));
        } finally {
            stopMotion(token, 1);
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
    void motionStatusReportsAMissingVideoDevice() {
        String token = login("Bob", "Bob");
        String original =
                (String) parameterNamed(cameraParameters(token, 1), "video_device").get("value");
        try {
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"value\":\"/dev/babymonitor-no-such-camera\"}")
                    .when()
                    .put("/api/cameras/1/parameters/video_device")
                    .then()
                    .statusCode(200);

            applyMotion(token, 1);

            // Motion keeps running and retries the device, so the process state
            // alone would look healthy; the device check is what surfaces this.
            given()
                    .header("Authorization", bearer(token))
                    .when()
                    .get("/api/cameras/1/motion")
                    .then()
                    .statusCode(200)
                    .body("running", equalTo(true))
                    .body("deviceAvailable", equalTo(false))
                    .body("error", containsString("/dev/babymonitor-no-such-camera"));
        } finally {
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body("{\"value\":\"" + original + "\"}")
                    .when()
                    .put("/api/cameras/1/parameters/video_device")
                    .then()
                    .statusCode(200);
            stopMotion(token, 1);
        }
    }

    @Test
    void motionStatusForAStoppedCameraIsNotRunning() {
        String token = login("Bob", "Bob");
        // Nothing is running at rest, so the app reports the camera as stopped.
        // Apply sets the desired state to running.
        stopMotion(token, 1);
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/1/motion")
                .then()
                .statusCode(200)
                .body("cameraNumber", equalTo(1))
                .body("running", equalTo(false))
                .body("pid", equalTo(null));
    }

    @Test
    void motionApplyWritesTheConfigAndStartsTheProcess() throws Exception {
        String token = login("Bob", "Bob");
        stopMotion(token, 1);

        Map<String, Object> status = applyMotion(token, 1);
        assertEquals(true, status.get("configExists"), "Apply should write the camera's config");
        assertTrue(((Number) status.get("pid")).longValue() > 0, "Apply should start Motion");
        assertEquals(true, status.get("running"));

        // The config is the shared template with this camera's rows appended, so
        // a hand-edited template is kept and only the per-camera values differ.
        String configFile = (String) status.get("configFile");
        String content = Files.readString(Path.of(configFile));
        assertTrue(content.contains("on_movie_end"), "the template should be copied in");
        assertTrue(
                content.contains("camera_name Nursery"),
                "the camera's rows should be appended to the template");
    }

    @Test
    void motionApplyReloadsARunningProcessInsteadOfStartingASecond() {
        String token = login("Bob", "Bob");
        stopMotion(token, 1);
        Long first = ((Number) applyMotion(token, 1).get("pid")).longValue();

        // A second Apply leaves the pid alone: the config is rewritten and the
        // running process is SIGHUP'd rather than duplicated.
        Long second = ((Number) applyMotion(token, 1).get("pid")).longValue();
        assertEquals(first, second, "Apply should reload the running process, not spawn another");

        stopMotion(token, 1);
    }

    @Test
    void motionStopKillsTheProcess() {
        String token = login("Bob", "Bob");
        assertTrue(((Number) applyMotion(token, 1).get("pid")).longValue() > 0);

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cameras/1/motion/stop")
                .then()
                .statusCode(200)
                .body("running", equalTo(false))
                .body("pid", equalTo(null));
    }

    // --- Camera enabled flag -----------------------------------------------

    @Test
    void applyMarksCameraRunning() {
        String token = login("Bob", "Bob");
        try {
            stopMotion(token, 1);
            Map<String, Object> status = applyMotion(token, 1);
            assertEquals(true, status.get("running"), "Apply should start and mark running");
            // Verify every parameter row now has enabled true
            List<Map<String, Object>> parameters = cameraParameters(token, 1);
            for (Map<String, Object> parameter : parameters) {
                assertEquals(Boolean.TRUE, parameter.get("enabled"), "every row carries enabled=true");
            }
        } finally {
            stopMotion(token, 1);
        }
    }

    @Test
    void stopMarksCameraNotRunning() {
        String token = login("Bob", "Bob");
        try {
            applyMotion(token, 1);
            stopMotion(token, 1);
            List<Map<String, Object>> parameters = cameraParameters(token, 1);
            for (Map<String, Object> parameter : parameters) {
                assertEquals(Boolean.FALSE, parameter.get("enabled"), "every row carries enabled=false");
            }
        } finally {
            stopMotion(token, 1);
        }
    }



    /** GETs a camera's Motion status body. */
    private static Map<String, Object> getMotion(String token, int cameraNumber) {
        return given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/" + cameraNumber + "/motion")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getObject("", Map.class);
    }

    /** POSTs an Apply and returns the status body. */
    private static Map<String, Object> applyMotion(String token, int cameraNumber) {
        return given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cameras/" + cameraNumber + "/motion/apply")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getObject("", Map.class);
    }

    /** Stops a camera so a test can start from a known state. */
    private static void stopMotion(String token, int cameraNumber) {
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cameras/" + cameraNumber + "/motion/stop")
                .then()
                .statusCode(200);
    }

    /** Stops a camera's audio so a test can start from a known state. */
    private static void stopCameraAudio(String token, int cameraNumber) {
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cameras/" + cameraNumber + "/audio/stop")
                .then()
                .statusCode(200);
    }
}
