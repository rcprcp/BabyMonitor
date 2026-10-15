package com.babymonitor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests against the running PostgreSQL database.
 *
 * <p>They rely on the rows seeded by {@code db/init.sql}: the
 * {@code streaming_url} and {@code systemName} settings, the four accounts
 * whose password is their own name, and the demo notifications. The webhook
 * tests write to {@code babymonitor.notifications} and dismiss the row they
 * created, so the table does not grow on every run.
 */
@QuarkusTest
class ApiTest {

    private static final String AUDIO_WEBHOOK = "/api/webhooks/audioNotification";
    private static final String VIDEO_WEBHOOK = "/api/webhooks/videoNotification";

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
                .body("token", not(emptyString()));
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
    void settingsReturnStreamingUrlWhenAuthenticated() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/settings/streaming_url")
                .then()
                .statusCode(200)
                .body("value", equalTo("http://192.168.0.2:8081"));
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

        Map<String, Object> row = rowFor(feed(token), "Baby cried in the nursery");
        assertEquals("2026-10-14T20:15:00Z", row.get("eventTime"));
        assertEquals("Audio", row.get("eventType"));
        assertNull(row.get("videoLink"), "audio notifications carry no video link");

        given()
                .header("Authorization", bearer(token))
                .when()
                .delete("/api/notifications/" + id)
                .then()
                .statusCode(204);
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

        Map<String, Object> row = rowFor(feed(token), "Baby cried again");
        assertEquals("Audio", row.get("eventType"));
        assertNull(row.get("videoLink"), "an audio event never keeps a video link");

        given()
                .header("Authorization", bearer(token))
                .when()
                .delete("/api/notifications/" + id)
                .then()
                .statusCode(204);
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

        Map<String, Object> row = rowFor(feed(token), "Motion detected");
        assertEquals("Video", row.get("eventType"));
        assertEquals("http://cam.local/clip.mp4", row.get("videoLink"));
        assertNull(row.get("severity"), "severity is no longer part of a notification");
        assertNull(row.get("read"), "read is no longer part of a notification");

        given()
                .header("Authorization", bearer(token))
                .when()
                .delete("/api/notifications/" + id)
                .then()
                .statusCode(204);
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
                .body("id", notNullValue());
    }
}
