package com.stubu.specdriven.timetracking;

import com.stubu.specdriven.timesheet.TimesheetStatus;

/** The work period belongs to a timesheet that is no longer a draft, so it cannot be corrected. */
public class EntryLockedException extends RuntimeException {

    private final TimesheetStatus status;

    public EntryLockedException(TimesheetStatus status) {
        super("The timesheet is " + status);
        this.status = status;
    }

    public TimesheetStatus getStatus() {
        return status;
    }
}
