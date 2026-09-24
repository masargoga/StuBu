package com.stubu.specdriven.monthlytimesheet;

import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.TimeEntry;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** The rules for submitting a timesheet, shared by the page (to show what is possible) and the service. */
final class SubmissionRules {

    private SubmissionRules() {
    }

    /**
     * What stops the timesheet from being submitted, if anything.
     *
     * @param entries      the work periods that started in the month
     * @param currentMonth the current month in the user's time zone; a month can be submitted once it is over
     */
    static Optional<SubmitBlocker> blocker(YearMonth month, TimesheetStatus status, List<TimeEntry> entries,
            YearMonth currentMonth) {
        if (status != TimesheetStatus.DRAFT) {
            return Optional.of(SubmitBlocker.NOT_DRAFT);
        }
        if (!month.isBefore(currentMonth)) {
            return Optional.of(SubmitBlocker.MONTH_NOT_ENDED);
        }
        if (entries.isEmpty()) {
            return Optional.of(SubmitBlocker.EMPTY);
        }
        if (entries.stream().anyMatch(TimeEntry::isActive)) {
            return Optional.of(SubmitBlocker.OPEN_ENTRY);
        }
        return Optional.empty();
    }
}
