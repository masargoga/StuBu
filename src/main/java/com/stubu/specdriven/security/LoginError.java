package com.stubu.specdriven.security;

import java.util.Arrays;
import java.util.Optional;

/** Why a login attempt ended in failure; the code is passed to the login page as {@code ?error=<code>}. */
public enum LoginError {
    /** UC-001 AF-1 */
    NOT_REGISTERED("not-registered"),
    /** UC-001 AF-2 */
    INACTIVE("inactive"),
    /** UC-001 AF-3 */
    UNAVAILABLE("unavailable"),
    /** UC-001 AF-4, and the fallback for anything unexpected */
    FAILED("failed");

    private final String code;

    LoginError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Translation key of the user-facing message. */
    public String messageKey() {
        return "login.error." + code;
    }

    public static Optional<LoginError> fromCode(String code) {
        return Arrays.stream(values()).filter(error -> error.code.equals(code)).findFirst();
    }
}
