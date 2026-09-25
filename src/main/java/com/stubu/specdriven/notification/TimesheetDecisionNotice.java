package com.stubu.specdriven.notification;

import java.time.Instant;
import java.time.YearMonth;

/**
 * What an employee needs to know when a manager has decided about their timesheet.
 *
 * @param recipientEmail the employee's email address
 * @param recipientName  the employee's full name
 * @param reviewerName   the full name of the manager who decided
 * @param reason         the rejection reason; {@code null} for an approval
 */
public record TimesheetDecisionNotice(String recipientEmail, String recipientName, String reviewerName,
        YearMonth period, Instant decidedAt, String reason) {
}
