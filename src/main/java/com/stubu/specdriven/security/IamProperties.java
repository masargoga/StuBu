package com.stubu.specdriven.security;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the OIDC identity providers offered on the login page, keyed by registration id
 * (e.g. {@code stubu.iam.providers.google.client-id}). A provider without a client id is ignored, so
 * an environment only shows the providers it has configured.
 *
 * <p>{@code google} and {@code microsoft} are known presets that only need credentials (Microsoft
 * additionally needs its {@code tenant-id}). Any other id is a generic OIDC provider and must define
 * the endpoint URIs explicitly. Every endpoint can also be overridden on a preset.
 */
@ConfigurationProperties(prefix = "stubu.iam")
public record IamProperties(Map<String, Provider> providers) {

    public IamProperties {
        providers = providers == null ? Map.of() : providers;
    }

    public record Provider(String displayName, String clientId, String clientSecret, String tenantId,
            String issuerUri, String authorizationUri, String tokenUri, String jwkSetUri, List<String> scopes) {
    }
}
