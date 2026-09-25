package com.stubu.specdriven.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * The health probes of Docker and Kubernetes (spec.md sections 47 and 48): reachable without signing in, only
 * "UP"/"DOWN" without details, and nothing else of the actuator is exposed. The application shuts down gracefully.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "vaadin.launch-browser=false", "vaadin.devmode.devTools.enabled=false" })
@ActiveProfiles("test")
class HealthEndpointsTest {

    @LocalServerPort
    int port;
    @Autowired
    Environment environment;

    private HttpResponse<String> get(String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void livenessAndReadinessAreUpAndNeedNoSignIn() throws Exception {
        for (String probe : new String[] { "/actuator/health/liveness", "/actuator/health/readiness" }) {
            HttpResponse<String> response = get(probe);
            assertEquals(200, response.statusCode(), probe);
            assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
        }
    }

    @Test
    void theOverallHealthShowsNoDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("components") || response.body().contains("jdbc")
                || response.body().contains("H2"), "No details about the database: " + response.body());
    }

    @Test
    void theReadinessProbeIncludesTheDatabase() {
        assertTrue(environment.getProperty("management.endpoint.health.group.readiness.include", "").contains("db"));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        for (String path : new String[] { "/actuator/env", "/actuator/beans", "/actuator/heapdump" }) {
            HttpResponse<String> response = get(path);
            assertTrue(response.statusCode() >= 300, path + " must not be readable: " + response.statusCode());
            assertFalse(response.body().contains("propertySources") || response.body().contains("beans\""), path);
        }
    }

    @Test
    void theApplicationShutsDownGracefully(@Value("${server.shutdown}") String shutdown) {
        assertEquals("graceful", shutdown);
    }
}
