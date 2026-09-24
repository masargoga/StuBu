package com.stubu.specdriven.security;

import com.stubu.specdriven.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.SocketException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Ends a failed login: audits it, clears the session and sends the user back to the login page with
 * a code that selects a user-friendly message. Technical details only go to the server log.
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

    /** Error codes an IAM provider returns when it is unable to serve the request. */
    private static final Set<String> PROVIDER_UNAVAILABLE_CODES = Set.of("server_error", "temporarily_unavailable");

    private final AuditService auditService;

    public LoginFailureHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        LoginError error = classify(exception);
        Long employeeId = exception instanceof LoginDeniedException denied ? denied.getEmployeeId() : null;
        String reason = describe(error, exception);
        log.warn("Login failed ({}): {}", error, reason, error == LoginError.FAILED ? exception : null);
        try {
            auditService.recordLoginFailure(employeeId, reason);
        } catch (RuntimeException e) {
            log.error("Could not write the audit record for a failed login", e);
        }
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/login?error=" + error.code());
    }

    static LoginError classify(AuthenticationException exception) {
        if (exception instanceof LoginDeniedException denied) {
            return denied.getReason() == LoginDeniedException.Reason.INACTIVE ? LoginError.INACTIVE
                    : LoginError.NOT_REGISTERED;
        }
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            if (PROVIDER_UNAVAILABLE_CODES.contains(oauth2.getError().getErrorCode()) || isProviderUnreachable(oauth2)) {
                return LoginError.UNAVAILABLE;
            }
        }
        return LoginError.FAILED;
    }

    /** True when the token exchange failed because the provider could not be reached or answered with a 5xx. */
    private static boolean isProviderUnreachable(Throwable exception) {
        for (Throwable t = exception; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ResourceAccessException || t instanceof SocketException
                    || (t instanceof RestClientResponseException r && r.getStatusCode().is5xxServerError())) {
                return true;
            }
        }
        return false;
    }

    private static String describe(LoginError error, AuthenticationException exception) {
        String detail = switch (error) {
            case NOT_REGISTERED, INACTIVE -> ((LoginDeniedException) exception).getEmail();
            default -> exception instanceof OAuth2AuthenticationException oauth2 ? oauth2.getError().getErrorCode()
                    : NestedExceptionUtils.getMostSpecificCause(exception).getClass().getSimpleName();
        };
        return error.name() + ": " + sanitize(detail);
    }

    /** Removes control characters so an attacker-controlled value cannot forge log or audit lines. */
    private static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("\\p{Cntrl}", "_");
    }
}
