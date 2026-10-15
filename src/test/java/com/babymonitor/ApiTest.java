package com.babymonitor;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests against the running PostgreSQL database.
 *
 * <p>They rely on the rows seeded by {@code db/init.sql}: the
 * {@code streaming_url} setting and the four accounts whose password is their
 * own name. The webhook test only touches the in-memory notification feed.
 */
@QuarkusTest
class ApiTest {

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
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/settings/streaming_url")
                .then()
                .statusCode(200)
                .body("value", equalTo("http://192.168.0.2:8081"));
    }

    @Test
    void audioWebhookIsAcceptedWithoutToken() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"event\":\"sound\",\"level\":42}")
                .when()
                .post("/api/webhooks/audioNotification")
                .then()
                .statusCode(202)
                .body("status", equalTo("accepted"));
    }

    @Test
    void videoWebhookIsAcceptedWithoutToken() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"event\":\"motion\"}")
                .when()
                .post("/api/webhooks/videoNotification")
                .then()
                .statusCode(202)
                .body("status", equalTo("accepted"));
    }

    @Test
    void userProfileRejectsMissingToken() {
        given().when().get("/api/users/me").then().statusCode(401);
    }

    @Test
    void userProfileReturnsDatabaseRecord() {
        String token = login("Bob", "Bob");
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("name", equalTo("Bob"))
                .body("id", notNullValue());
    }
}
