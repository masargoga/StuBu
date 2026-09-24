package com.stubu.specdriven.timetracking;

/** An entered work period is not acceptable. */
public class InvalidWorkPeriodException extends RuntimeException {

    public enum Reason {
        /** No start was given. */
        MISSING_START,
        /** The start is not before the current server time. */
        START_NOT_IN_PAST,
        /** The period overlaps another work period of the employee. */
        OVERLAP,
        /** A corrected check-out is not after the check-in. */
        CHECK_OUT_BEFORE_CHECK_IN,
        /** A corrected check-in or check-out lies in the future. */
        IN_THE_FUTURE,
        /** A completed work period lost its check-out. */
        CHECK_OUT_REQUIRED,
        /** The date of a work period cannot be changed. */
        DATE_CHANGED
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
