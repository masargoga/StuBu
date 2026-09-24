package com.stubu.specdriven.security;

import java.util.Map;
import java.util.Optional;

/**
 * Extracts the authenticated user's email address from the claims an IAM provider issued. Keeps
 * provider-specific claim conventions out of the domain logic.
 */
public interface IamEmailExtractor {

    /**
     * @param claims         claims of the validated ID token
     * @param registrationId id of the provider that issued them
     * @return the email address, or empty when the provider did not supply a usable, verified one
     */
    Optional<String> extractEmail(Map<String, Object> claims, String registrationId);
}
