package com.stubu.specdriven.timetracking;

import com.vaadin.flow.component.html.Div;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** Draws work periods as bars on a track that shows the {@link TimelineWindow} of the day; a bar is striped while its period is still open. */
public final class DayTrack {

    private DayTrack() {
    }

    /** A track for one day with a bar for every period. */
    public static Div forDay(List<TimeEntry> entries, LocalDate date, ZoneId zone, Instant now, TimelineWindow window) {
        Instant dayStart = window.startOn(date, zone);
        Instant dayEnd = window.endOn(date, zone);
        Div track = new Div();
        track.addClassName("timeline-track");
        track.getElement().setAttribute("aria-hidden", "true");
        entries.forEach(entry -> track.add(bar(entry, dayStart, dayEnd, now)));
        return track;
    }

    /** The bar of one period, positioned and sized as a share of the visible time span. */
    public static Div bar(TimeEntry entry, Instant dayStart, Instant dayEnd, Instant now) {
        Instant end = entry.isActive() ? now : entry.getCheckOutAt();
        double dayLength = Duration.between(dayStart, dayEnd).toMillis();
        double from = clamp(Duration.between(dayStart, entry.getCheckInAt()).toMillis() / dayLength);
        double to = clamp(Duration.between(dayStart, end).toMillis() / dayLength);

        Div bar = new Div();
        bar.addClassName("timeline-bar");
        bar.setClassName("timeline-bar-open", entry.isActive());
        bar.getStyle().set("left", percent(from)).set("width", percent(Math.max(to - from, 0)));
        return bar;
    }

    /** A share of the visible time span as a CSS percentage. */
    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.3f%%", fraction * 100);
    }

    private static double clamp(double fraction) {
        return Math.min(1, Math.max(0, fraction));
    }
}
