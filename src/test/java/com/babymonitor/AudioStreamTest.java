package com.babymonitor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The live-listen stream: the same PCM that feeds YAMNet must reach a browser as
 * an authenticated, endless chunked response. The capture and classifier are
 * stubbed, so this exercises the relay rather than a microphone.
 */
@QuarkusTest
@QuarkusTestResource(AudioTestResource.class)
class AudioStreamTest {

    @TestHTTPResource("/")
    URI baseUri;

    @Test
    void streamsTheCapturedPcmForACamera() throws Exception {
        String token = login();
        startAudio(token, 1);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(baseUri.resolve("api/cameras/1/audio/stream"))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            byte[] pcm = assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                HttpResponse<InputStream> response =
                        client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                assertEquals(200, response.statusCode());
                assertEquals(
                        "16000",
                        response.headers().firstValue("X-Audio-Sample-Rate").orElse(null));
                assertEquals("1", response.headers().firstValue("X-Audio-Channels").orElse(null));
                try (InputStream body = response.body()) {
                    return body.readNBytes(4096);
                }
            });

            assertEquals(4096, pcm.length, "the stream should deliver captured PCM");
        } finally {
            stopAudio(token, 1);
        }
    }

    @Test
    void streamRequiresToken() {
        given().when().get("/api/cameras/1/audio/stream").then().statusCode(401);
    }

    @Test
    void streamForAnUnknownCameraIsNotFound() {
        String token = login();
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/cameras/999/audio/stream")
                .then()
                .statusCode(404)
                .body("error", equalTo("No such camera: 999"));
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

    private static void startAudio(String token, int cameraNumber) {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cameras/" + cameraNumber + "/audio/start")
                .then()
                .statusCode(200)
                .body("running", equalTo(true));
    }

    private static void stopAudio(String token, int cameraNumber) {
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .when()
                .post("/api/cameras/" + cameraNumber + "/audio/stop")
                .then()
                .statusCode(200);
    }
}
