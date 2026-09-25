package com.stubu.specdriven.approval;

import com.stubu.specdriven.timesheet.TimesheetStatus;

/** Only a submitted timesheet can be approved or rejected. */
public class ReviewStatusException extends RuntimeException {

    private final TimesheetStatus status;

    public ReviewStatusException(TimesheetStatus status) {
        super("The timesheet is " + status + ", not SUBMITTED");
        this.status = status;
    }

    public TimesheetStatus getStatus() {
        return status;
    }
}
