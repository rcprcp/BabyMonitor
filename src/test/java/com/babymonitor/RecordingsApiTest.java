package com.babymonitor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests for the recordings route.
 *
 * <p>They point camera 2's {@code target_dir} at a temporary directory, write a
 * clip into it, exercise the route, and put the row back in a {@code finally} --
 * so the seeded configuration is left as it was found.
 */
@QuarkusTest
class RecordingsApiTest {

    private static final int CAMERA = 2;

    @TempDir
    Path directory;

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

    /** Reads one camera parameter's value through the API. */
    private static String parameterValue(String token, int cameraNumber, String key) {
        List<Map<String, Object>> parameters = given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/" + cameraNumber)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
        return parameters.stream()
                .filter(row -> key.equals(row.get("key")))
                .map(row -> (String) row.get("value"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no parameter named: " + key));
    }

    private static void setParameter(String token, int cameraNumber, String key, String value) {
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("{\"value\":\"" + value + "\"}")
                .when()
                .put("/api/cameras/" + cameraNumber + "/parameters/" + key)
                .then()
                .statusCode(200);
    }

    @Test
    void recordingsRequireAToken() {
        given().when().get("/api/cameras/2/recordings").then().statusCode(401);
        given().when().get("/api/cameras/2/recordings/clip.mp4").then().statusCode(401);
    }

    @Test
    void recordingsForAnUnknownCameraAreNotFound() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/999/recordings")
                .then()
                .statusCode(404)
                .body("error", equalTo("No such camera: 999"));
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/999/recordings/clip.mp4")
                .then()
                .statusCode(404);
    }

    @Test
    void recordingsAreListedAndServedWithRanges() throws IOException {
        String token = login("Bob", "Bob");
        String original = parameterValue(token, CAMERA, "target_dir");

        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("clip.mp4"), content);
        Files.writeString(directory.resolve(".hidden"), "hidden");
        Files.createDirectory(directory.resolve("nested"));

        try {
            setParameter(token, CAMERA, "target_dir", directory.toString());

            // The list only sees the regular, non-hidden file.
            List<Map<String, Object>> clips = given()
                    .header("Authorization", bearer(token))
                    .when()
                    .get("/api/cameras/" + CAMERA + "/recordings")
                    .then()
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getList("$");
            assertEquals(1, clips.size(), "only the visible file is listed");
            assertEquals("clip.mp4", clips.get(0).get("name"));
            assertEquals(
                    (long) content.length,
                    ((Number) clips.get(0).get("sizeBytes")).longValue());
            String url = (String) clips.get(0).get("url");
            assertEquals("/api/cameras/2/recordings/clip.mp4", url);

            // The whole clip.
            byte[] served = given()
                    .header("Authorization", bearer(token))
                    .when()
                    .get(url)
                    .then()
                    .statusCode(200)
                    .header("Content-Type", containsString("video/mp4"))
                    .header("Accept-Ranges", equalTo("bytes"))
                    .extract()
                    .asByteArray();
            assertArrayEquals(content, served);

            // A byte range is answered with 206 and just that slice.
            byte[] slice = given()
                    .header("Authorization", bearer(token))
                    .header("Range", "bytes=2-5")
                    .when()
                    .get(url)
                    .then()
                    .statusCode(206)
                    .header("Content-Range", equalTo("bytes 2-5/" + content.length))
                    .extract()
                    .asByteArray();
            assertArrayEquals("2345".getBytes(StandardCharsets.UTF_8), slice);
        } finally {
            setParameter(token, CAMERA, "target_dir", original);
        }
    }

    @Test
    void recordingNamesCannotEscapeTheCameraDirectory() {
        String token = login("Bob", "Bob");

        // Encoded dots are not decoded into "..", so this is simply a name that
        // does not exist; either way it is never served. The "not a recording
        // name" refusal itself is covered by RecordingsServiceTest.
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/2/recordings/%2e%2e")
                .then()
                .statusCode(oneOf(400, 404));

        // An encoded slash either reaches the handler (and is refused) or does
        // not match the single-segment route at all; it must never be served.
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/2/recordings/%2e%2e%2f%2e%2e%2fetc%2fpasswd")
                .then()
                .statusCode(oneOf(400, 404));

        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/cameras/2/recordings/missing.mp4")
                .then()
                .statusCode(404)
                .body("error", containsString("No such recording"));
    }
}
