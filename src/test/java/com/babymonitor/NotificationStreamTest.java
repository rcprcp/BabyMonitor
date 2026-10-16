package com.babymonitor;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.babymonitor.entity.Notification;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * End-to-end tests for the server-sent event stream that pushes new
 * notifications to the SPA.
 *
 * <p>{@code EventSource} cannot set an {@code Authorization} header, so the
 * stream is the one endpoint that also accepts the session token in a query
 * parameter; these tests cover that carve-out, the catch-up a reconnecting
 * client receives, and the live push when a webhook records an event.
 *
 * <p>The live tests hold a connection open, so they read with a deadline and
 * close the body when done. Rows they create are removed again in a
 * {@code finally}, matching {@link ApiTest}.
 */
@QuarkusTest
class NotificationStreamTest {

    private static final String STREAM_PATH = "api/notifications/stream";
    private static final String AUDIO_WEBHOOK = "/api/webhooks/audioNotification";

    @TestHTTPResource
    URI baseUri;

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

    private static long maxNotificationId(String token) {
        List<Map<String, Object>> feed = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/notifications")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
        return feed.stream()
                .mapToLong(row -> ((Number) row.get("id")).longValue())
                .max()
                .orElse(0L);
    }

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

    private static void deleteNotificationRow(long id) {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteById(id));
    }

    private URI streamUri(String token, long lastId) {
        return URI.create(baseUri + STREAM_PATH + "?token=" + token + "&lastId=" + lastId);
    }

    private static HttpResponse<Stream<String>> openStream(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofLines());
    }

    @Test
    void streamRequiresAToken() {
        given().when().get("/api/notifications/stream").then().statusCode(401);
    }

    /**
     * The query-parameter token is only accepted for the stream path; everywhere
     * else it is ignored, so the token cannot be smuggled in via a URL.
     */
    @Test
    void queryTokenIsNotAcceptedOnOtherPaths() {
        String token = login("Bob", "Bob");
        given()
                .when()
                .get("/api/notifications?token=" + token)
                .then()
                .statusCode(401);
    }

    /**
     * A reconnecting client is sent everything newer than the id it supplies, so
     * an event recorded while it was away is not lost.
     */
    @Test
    @Timeout(15)
    void streamReplaysEventsNewerThanLastId() throws Exception {
        String token = login("Bob", "Bob");
        String description = "catch-up event " + System.nanoTime();
        long id = postAudioWebhook(description);

        HttpResponse<Stream<String>> response = null;
        try {
            response = openStream(streamUri(token, id - 1));
            assertEquals(200, response.statusCode());

            String event = firstEventContaining(response.body(), description, Duration.ofSeconds(10));
            assertNotNull(event, "the event newer than lastId should be replayed");
        } finally {
            close(response);
            deleteNotificationRow(id);
        }
    }

    /**
     * A connected client is pushed an event as soon as the webhook records it,
     * without polling.
     */
    @Test
    @Timeout(15)
    void streamPushesANewEventToAConnectedClient() throws Exception {
        String token = login("Bob", "Bob");
        long lastId = maxNotificationId(token);

        HttpResponse<Stream<String>> response = null;
        String description = "live push event " + System.nanoTime();
        long id = -1;
        try {
            response = openStream(streamUri(token, lastId));
            assertEquals(200, response.statusCode());

            // Recorded after the stream is open, so it can only arrive by push.
            id = postAudioWebhook(description);

            String event = firstEventContaining(response.body(), description, Duration.ofSeconds(10));
            assertNotNull(event, "the live event should have been pushed to the stream");
        } finally {
            close(response);
            if (id >= 0) {
                deleteNotificationRow(id);
            }
        }
    }

    /**
     * Reads SSE lines until one carries the given description, or the deadline
     * passes. Runs the read on a bounded schedule by polling the stream line by
     * line with a wall-clock budget.
     */
    private static String firstEventContaining(Stream<String> lines, String needle, Duration timeout)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        var iterator = lines.iterator();
        while (System.currentTimeMillis() < deadline) {
            // hasNext() blocks until a line or EOF; the request timeout bounds it.
            if (!iterator.hasNext()) {
                return null;
            }
            String line = iterator.next();
            if (line.startsWith("data:") && line.contains(needle)) {
                return line;
            }
        }
        return null;
    }

    private static void close(HttpResponse<Stream<String>> response) {
        if (response != null) {
            try {
                response.body().close();
            } catch (Exception ignored) {
                // Best effort.
            }
        }
    }
}
