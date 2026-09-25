package com.stubu.specdriven.approval;

import java.time.Instant;
import java.time.YearMonth;

/** A submitted timesheet waiting for a decision. */
public record PendingApproval(long timesheetId, String employeeName, YearMonth period, Instant submittedAt) {
}
