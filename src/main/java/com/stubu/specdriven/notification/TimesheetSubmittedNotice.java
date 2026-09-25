package com.stubu.specdriven.notification;

import java.time.Instant;
import java.time.YearMonth;

/**
 * What a manager needs to know when an employee submits a timesheet.
 *
 * @param recipientEmail the manager's email address
 * @param recipientName  the manager's full name
 * @param employeeName   the full name of the employee who submitted
 * @param resubmission   whether a rejected timesheet was corrected and submitted again
 */
public record TimesheetSubmittedNotice(String recipientEmail, String recipientName, String employeeName,
        YearMonth period, Instant submittedAt, boolean resubmission) {
}
