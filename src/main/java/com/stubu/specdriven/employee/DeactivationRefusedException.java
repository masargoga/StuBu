package com.stubu.specdriven.employee;

/** An employee cannot be deactivated. */
public class DeactivationRefusedException extends RuntimeException {

    public enum Reason {
        /** Administrators do not lock themselves out. */
        SELF,
        /** Somebody has to stay an administrator. */
        LAST_ADMIN,
        ALREADY_INACTIVE
    }

    private final Reason reason;

    public DeactivationRefusedException(Reason reason) {
        super("Cannot deactivate: " + reason);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
