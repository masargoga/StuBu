package com.stubu.specdriven.timesheet;

public enum TimesheetStatus {
    DRAFT, SUBMITTED, APPROVED, REJECTED;

    /** Time entries can be corrected while the timesheet is a draft, and after a rejection to fix what was wrong. */
    public boolean allowsCorrections() {
        return this == DRAFT || this == REJECTED;
    }
}
