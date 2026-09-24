package com.stubu.specdriven.security;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.util.StringUtils;

/**
 * The IAM abstraction: turns {@link IamProperties} into Spring Security client registrations. Nothing
 * here (or downstream) depends on a particular provider beyond the endpoint presets. Endpoints are
 * configured statically, so the application starts even while an identity provider is unreachable.
 */
public final class IamClientRegistrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    private static final String MICROSOFT_LOGIN = "https://login.microsoftonline.com/";

    /** Sorted by registration id so the login page lists providers in a stable order. */
    private final Map<String, ClientRegistration> registrations = new TreeMap<>();

    public IamClientRegistrations(IamProperties properties) {
        properties.providers().forEach((id, provider) -> {
            if (provider != null && StringUtils.hasText(provider.clientId())) {
                registrations.put(id, build(id, provider));
            }
        });
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return registrations.get(registrationId);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return registrations.values().iterator();
    }

    private static ClientRegistration build(String id, IamProperties.Provider p) {
        ClientRegistration.Builder builder = switch (id) {
            case "google" -> CommonOAuth2Provider.GOOGLE.getBuilder(id);
            case "microsoft" -> microsoft(id, p);
            default -> generic(id);
        };
        builder.clientId(p.clientId()).clientSecret(p.clientSecret());
        if (StringUtils.hasText(p.displayName())) {
            builder.clientName(p.displayName());
        }
        if (StringUtils.hasText(p.authorizationUri())) {
            builder.authorizationUri(p.authorizationUri());
        }
        if (StringUtils.hasText(p.tokenUri())) {
            builder.tokenUri(p.tokenUri());
        }
        if (StringUtils.hasText(p.jwkSetUri())) {
            builder.jwkSetUri(p.jwkSetUri());
        }
        if (StringUtils.hasText(p.issuerUri())) {
            builder.issuerUri(p.issuerUri());
        }
        if (p.scopes() != null && !p.scopes().isEmpty()) {
            builder.scope(p.scopes());
        }
        try {
            return builder.build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IllegalStateException("Invalid configuration for IAM provider '" + id
                    + "' (stubu.iam.providers." + id + "): " + e.getMessage(), e);
        }
    }

    private static ClientRegistration.Builder microsoft(String id, IamProperties.Provider p) {
        if (!StringUtils.hasText(p.tenantId())) {
            throw new IllegalStateException("stubu.iam.providers.microsoft.tenant-id is required");
        }
        String tenant = MICROSOFT_LOGIN + p.tenantId();
        return base(id).clientName("Microsoft")
                .authorizationUri(tenant + "/oauth2/v2.0/authorize")
                .tokenUri(tenant + "/oauth2/v2.0/token")
                .jwkSetUri(tenant + "/discovery/v2.0/keys")
                .issuerUri(tenant + "/v2.0");
    }

    private static ClientRegistration.Builder generic(String id) {
        return base(id).clientName(id);
    }

    private static ClientRegistration.Builder base(String id) {
        return ClientRegistration.withRegistrationId(id)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(List.of("openid", "profile", "email"))
                .userNameAttributeName(IdTokenClaimNames.SUB);
    }
}
