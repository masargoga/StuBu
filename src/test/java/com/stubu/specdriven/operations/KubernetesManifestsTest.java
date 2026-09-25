package com.stubu.specdriven.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The Kubernetes manifests in {@code deploy/kubernetes} are valid YAML and agree with the application: the probe paths
 * exist, the graceful shutdown fits into the termination grace period, the container is not run as root, and no real
 * secret is committed (spec.md sections 46 to 48).
 */
class KubernetesManifestsTest {

    private static final Path DIRECTORY = Path.of("deploy", "kubernetes");

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> documents(Path file) throws IOException {
        List<Map<String, Object>> documents = new ArrayList<>();
        try (var reader = Files.newBufferedReader(file)) {
            new Yaml().loadAll(reader).forEach(document -> documents.add((Map<String, Object>) document));
        }
        return documents;
    }

    private static Map<String, Object> manifest(String fileName) throws IOException {
        List<Map<String, Object>> documents = documents(DIRECTORY.resolve(fileName));
        assertEquals(1, documents.size(), fileName);
        return documents.get(0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T at(Object node, String... path) {
        Object current = node;
        for (String key : path) {
            assertNotNull(current, "Missing " + String.join(".", path));
            current = ((Map<String, Object>) current).get(key);
        }
        return (T) current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> container() throws IOException {
        return ((List<Map<String, Object>>) at(manifest("deployment.yaml"), "spec", "template", "spec", "containers"))
                .get(0);
    }

    private static Properties applicationProperties() throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src", "main", "resources", "application.properties"))) {
            properties.load(in);
        }
        return properties;
    }

    @Test
    void everyManifestIsValidYamlWithAKind() throws IOException {
        try (Stream<Path> files = Files.walk(DIRECTORY)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yaml")).toList()) {
                for (Map<String, Object> document : documents(file)) {
                    assertNotNull(document.get("kind"), file + " has a kind");
                    assertNotNull(at(document, "metadata", "name"), file + " has a name");
                }
            }
        }
    }

    @Test
    void theProbesUseTheHealthEndpointsOfTheApplication() throws IOException {
        Map<String, Object> container = container();

        assertEquals("/actuator/health/liveness", at(container.get("livenessProbe"), "httpGet", "path"));
        assertEquals("/actuator/health/liveness", at(container.get("startupProbe"), "httpGet", "path"));
        assertEquals("/actuator/health/readiness", at(container.get("readinessProbe"), "httpGet", "path"));
        assertTrue(applicationProperties().getProperty("management.endpoint.health.probes.enabled").equals("true"));
    }

    @Test
    void theContainerListensOnThePortTheApplicationUses() throws IOException {
        // server.port=${PORT:8080}
        assertTrue(applicationProperties().getProperty("server.port").endsWith(":8080}"));
        List<Map<String, Object>> ports = at(container(), "ports");
        assertEquals(8080, ports.get(0).get("containerPort"));
    }

    @Test
    void theGracefulShutdownFitsIntoTheTerminationGracePeriod() throws IOException {
        Properties properties = applicationProperties();
        assertEquals("graceful", properties.getProperty("server.shutdown"));
        int shutdownSeconds = Integer.parseInt(properties.getProperty("spring.lifecycle.timeout-per-shutdown-phase")
                .replace("s", ""));
        int grace = at(manifest("deployment.yaml"), "spec", "template", "spec", "terminationGracePeriodSeconds");
        List<String> preStop = at(container(), "lifecycle", "preStop", "exec", "command");
        int preStopSeconds = Integer.parseInt(preStop.get(1));

        assertTrue(grace >= shutdownSeconds + preStopSeconds, "The pod gets " + grace + "s, the shutdown needs "
                + (shutdownSeconds + preStopSeconds) + "s");
    }

    @Test
    void theContainerRunsWithoutRootAndWithoutWritingToItsFileSystem() throws IOException {
        assertEquals(true, at(manifest("deployment.yaml"), "spec", "template", "spec", "securityContext", "runAsNonRoot"));
        // Kubernetes cannot check a user name against runAsNonRoot: the user is a number, the same as in the Dockerfile.
        int user = at(manifest("deployment.yaml"), "spec", "template", "spec", "securityContext", "runAsUser");
        assertTrue(user > 0);
        assertTrue(Files.readString(Path.of("Dockerfile")).contains("USER " + user), "The image runs as the same user");
        assertEquals(true, at(container(), "securityContext", "readOnlyRootFilesystem"));
        assertEquals(false, at(container(), "securityContext", "allowPrivilegeEscalation"));
    }

    @Test
    void severalPodsRunBehindAnIngressThatKeepsABrowserOnOnePod() throws IOException {
        assertTrue((Integer) at(manifest("deployment.yaml"), "spec", "replicas") >= 2);
        assertEquals("cookie", at(manifest("ingress.yaml"), "metadata", "annotations",
                "nginx.ingress.kubernetes.io/affinity"));
    }

    @Test
    void noSecretIsCommittedNextToTheDeployment() throws IOException {
        try (Stream<Path> files = Files.list(DIRECTORY)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yaml")).toList()) {
                for (Map<String, Object> document : documents(file)) {
                    assertFalse("Secret".equals(document.get("kind")), file + " must not contain a Secret");
                }
            }
        }
        Path example = DIRECTORY.resolve("examples").resolve("secret.example.yaml");
        assertTrue(Files.readString(example).contains("REPLACE_ME"), "The example only has placeholders");
    }
}
