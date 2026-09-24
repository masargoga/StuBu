package com.stubu.specdriven.timetracking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One day of an employee's work periods with the derived totals.
 *
 * @param date            the day, in the time zone it was calculated for
 * @param entries         the day's work periods, earliest check-in first (plus an open period started earlier)
 * @param worked          full duration of every period, with the elapsed time for an open period
 * @param openElapsed     the part of {@code worked} that is the elapsed time of the open period (zero if none)
 * @param breaks          sum of the gaps between consecutive periods
 * @param lockedEntryIds  the periods that can no longer be corrected because their timesheet was submitted
 */
public record DaySummary(LocalDate date, List<TimeEntry> entries, Duration worked, Duration openElapsed,
        Duration breaks, Set<Long> lockedEntryIds) {

    /** Derives worked and break time from the periods, which must be ordered by check-in time. */
    public static DaySummary of(LocalDate date, List<TimeEntry> entries, Instant now) {
        return of(date, entries, now, Set.of());
    }

    public static DaySummary of(LocalDate date, List<TimeEntry> entries, Instant now, Set<Long> lockedEntryIds) {
        Duration worked = Duration.ZERO;
        Duration openElapsed = Duration.ZERO;
        Duration breaks = Duration.ZERO;
        TimeEntry previous = null;
        for (TimeEntry entry : entries) {
            Duration duration = entry.durationAt(now);
            worked = worked.plus(duration);
            if (entry.isActive()) {
                openElapsed = openElapsed.plus(duration);
            }
            if (previous != null && previous.getCheckOutAt() != null
                    && entry.getCheckInAt().isAfter(previous.getCheckOutAt())) {
                breaks = breaks.plus(Duration.between(previous.getCheckOutAt(), entry.getCheckInAt()));
            }
            previous = entry;
        }
        return new DaySummary(date, List.copyOf(entries), worked, openElapsed, breaks, Set.copyOf(lockedEntryIds));
    }

    /** The period the employee is working in right now, if any. */
    public Optional<TimeEntry> open() {
        return entries.stream().filter(TimeEntry::isActive).findFirst();
    }

    /** The worked time of the completed periods only. */
    public Duration completed() {
        return worked.minus(openElapsed);
    }

    /** Whether the period may still be corrected or deleted. */
    public boolean isEditable(TimeEntry entry) {
        return !lockedEntryIds.contains(entry.getId());
    }
}
