package com.stubu.specdriven.security;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** The IAM system authenticated the user, but the application refuses access to that identity. */
public class LoginDeniedException extends OAuth2AuthenticationException {

    public enum Reason {
        /** No employee record exists for the authenticated email address. */
        NOT_REGISTERED,
        /** An employee record exists but the employee is deactivated. */
        INACTIVE
    }

    private final Reason reason;
    private final String email;
    private final Long employeeId;

    public LoginDeniedException(Reason reason, String email, Long employeeId) {
        super(new OAuth2Error("employee_" + reason.name().toLowerCase(), "Login denied: " + reason, null));
        this.reason = reason;
        this.email = email;
        this.employeeId = employeeId;
    }

    public Reason getReason() {
        return reason;
    }

    public String getEmail() {
        return email;
    }

    /** The matching employee's id, or {@code null} when there is none. */
    public Long getEmployeeId() {
        return employeeId;
    }
}
