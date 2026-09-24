package com.stubu.specdriven.timesheet;

public enum TimesheetStatus {
    DRAFT, SUBMITTED, APPROVED, REJECTED;

    /** Time entries of a timesheet can only be corrected while it is a draft. */
    public boolean allowsCorrections() {
        return this == DRAFT;
    }
}
