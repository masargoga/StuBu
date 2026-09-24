package com.stubu.specdriven.security;

import com.stubu.specdriven.employee.Employee;
import java.util.Map;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Called by Spring Security after the authorization code was exchanged and the ID token validated.
 * Extracts the email from the ID token, resolves the active employee and establishes the user
 * context with the employee's roles.
 */
@Component
public class EmployeeOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    static final String MISSING_EMAIL = "missing_email";

    private final IamEmailExtractor emailExtractor;
    private final AuthenticationService authenticationService;

    public EmployeeOidcUserService(IamEmailExtractor emailExtractor, AuthenticationService authenticationService) {
        this.emailExtractor = emailExtractor;
        this.authenticationService = authenticationService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> claims = userRequest.getIdToken().getClaims();
        String email = emailExtractor.extractEmail(claims, registrationId)
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(MISSING_EMAIL,
                        "The identity provider did not supply a verified email address.", null)));
        Employee employee = authenticationService.resolveActiveEmployee(email);
        return new EmployeePrincipal(employee, userRequest.getIdToken());
    }
}
