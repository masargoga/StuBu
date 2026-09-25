package com.stubu.specdriven.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * The icon mark is shown on the login page and as the browser tab icon, so it has to be readable before anybody has
 * signed in; the stylesheet as well. Everything else needs a login.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "vaadin.launch-browser=false", "vaadin.devmode.devTools.enabled=false" })
@ActiveProfiles("test")
class StaticResourcesTest {

    @LocalServerPort
    int port;

    private HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theIconMarkIsAnImageForAnonymousVisitors() throws Exception {
        HttpResponse<String> response = get("/icons/stubu-mark.svg");

        assertEquals(200, response.statusCode(), "Not redirected to the login page");
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("svg"),
                response.headers().firstValue("content-type").orElse("none"));
        assertTrue(response.body().contains("<svg"), response.body());
    }

    @Test
    void theStylesheetIsAvailableForTheLoginPage() throws Exception {
        assertEquals(200, get("/styles.css").statusCode());
    }

    @Test
    void anApplicationPageStillNeedsALogin() throws Exception {
        HttpResponse<String> response = get("/timesheet");

        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("location").orElse("").endsWith("/login"));
    }
}
