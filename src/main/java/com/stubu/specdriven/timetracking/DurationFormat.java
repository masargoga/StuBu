package com.stubu.specdriven.timetracking;

import java.time.Duration;

/** Formats durations the way employees read them: "8h 16m". */
public final class DurationFormat {

    private DurationFormat() {
    }

    /** Whole minutes, rounded down; negative durations count as zero. */
    public static String format(Duration duration) {
        long minutes = Math.max(0, duration.toMinutes());
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
}
