package com.stubu.specdriven.timetracking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The part of the day a timeline shows. Most work happens between six in the morning and eight in the evening, so
 * that is the default; it grows in whole hours when a work period starts earlier or ends later, so every period is
 * always fully visible. A month view uses the union of all its days so that the rows can be compared.
 *
 * @param startHour first hour shown, 0 to 23
 * @param endHour   last hour shown, at most 24 and after {@code startHour}
 */
public record TimelineWindow(int startHour, int endHour) {

    public static final int DEFAULT_START = 6;
    public static final int DEFAULT_END = 20;
    public static final TimelineWindow DEFAULT = new TimelineWindow(DEFAULT_START, DEFAULT_END);

    public TimelineWindow {
        if (startHour < 0 || endHour > 24 || endHour <= startHour) {
            throw new IllegalArgumentException("Invalid window " + startHour + "-" + endHour);
        }
    }

    /** The default window, widened to fit every given period of the day (open periods end now). */
    public static TimelineWindow forDay(LocalDate date, List<TimeEntry> entries, ZoneId zone, Instant now) {
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        int start = DEFAULT_START;
        int end = DEFAULT_END;
        for (TimeEntry entry : entries) {
            Instant from = max(entry.getCheckInAt(), dayStart);
            Instant to = min(entry.isActive() ? now : entry.getCheckOutAt(), dayEnd);
            if (!to.isAfter(from)) {
                continue;
            }
            start = Math.min(start, (int) Math.floor(hoursSince(dayStart, from)));
            end = Math.max(end, (int) Math.ceil(hoursSince(dayStart, to)));
        }
        return new TimelineWindow(Math.max(start, 0), Math.min(end, 24));
    }

    /** The smallest window that contains both. */
    public TimelineWindow union(TimelineWindow other) {
        return new TimelineWindow(Math.min(startHour, other.startHour), Math.max(endHour, other.endHour));
    }

    /** Where the window begins on the given day. */
    public Instant startOn(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant().plus(Duration.ofHours(startHour));
    }

    /** Where the window ends on the given day. */
    public Instant endOn(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant().plus(Duration.ofHours(endHour));
    }

    /** The hours labelled on the axis: every two hours, or every three or four when the window is wide. */
    public int[] ticks() {
        int length = endHour - startHour;
        int step = length <= 14 ? 2 : length <= 18 ? 3 : 4;
        int count = length / step + 1;
        int[] hours = new int[count];
        for (int i = 0; i < count; i++) {
            hours[i] = startHour + i * step;
        }
        return hours;
    }

    /** The position of an hour on the axis, from 0 (start of the window) to 1 (end). */
    public double fraction(int hour) {
        return (hour - startHour) / (double) (endHour - startHour);
    }

    private static double hoursSince(Instant from, Instant to) {
        return Duration.between(from, to).toMinutes() / 60.0;
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
