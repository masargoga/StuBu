package com.stubu.specdriven.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reads the email from the standard {@code email} claim. Microsoft Entra ID may omit it and only
 * issue {@code preferred_username} / {@code upn}, which hold the user principal name (an email-like
 * value), so those are used as fallbacks. An address the provider explicitly flags as unverified is
 * never trusted.
 */
@Component
class OidcClaimsEmailExtractor implements IamEmailExtractor {

    private static final List<String> EMAIL_CLAIMS = List.of("email", "preferred_username", "upn");

    @Override
    public Optional<String> extractEmail(Map<String, Object> claims, String registrationId) {
        if (isExplicitlyUnverified(claims.get("email_verified"))) {
            return Optional.empty();
        }
        return EMAIL_CLAIMS.stream()
                .map(claims::get)
                .filter(String.class::isInstance)
                .map(value -> ((String) value).trim())
                .filter(value -> value.indexOf('@') > 0)
                .findFirst();
    }

    private static boolean isExplicitlyUnverified(Object verified) {
        return Boolean.FALSE.equals(verified) || (verified instanceof String s && "false".equalsIgnoreCase(s));
    }
}
