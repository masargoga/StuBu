package com.stubu.specdriven.monthlytimesheet;

import com.stubu.specdriven.timesheet.TimesheetStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * An employee's timesheet for one calendar month: every day of the month, the totals and the approval status.
 *
 * @param approvedByName the manager who approved it, if it was approved
 * @param submitBlocker  what stops the timesheet from being submitted, or {@code null} if it can be submitted
 */
public record MonthlyTimesheet(YearMonth period, TimesheetStatus status, Instant submittedAt, Instant approvedAt,
        String approvedByName, Instant rejectedAt, String rejectionReason, List<DayLine> days,
        Duration totalWorked, Duration totalBreaks, SubmitBlocker submitBlocker) {

    /** Whether the entries of this month can still be corrected. */
    public boolean editable() {
        return status.allowsCorrections();
    }
}
