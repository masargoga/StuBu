package com.stubu.specdriven.timetracking;

/** An entered work period is not acceptable. */
public class InvalidWorkPeriodException extends RuntimeException {

    public enum Reason {
        /** No start was given. */
        MISSING_START,
        /** The start is not before the current server time. */
        START_NOT_IN_PAST,
        /** The period overlaps another work period of the employee. */
        OVERLAP
    }

    private final Reason reason;

    public InvalidWorkPeriodException(Reason reason) {
        super("Invalid work period: " + reason);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
