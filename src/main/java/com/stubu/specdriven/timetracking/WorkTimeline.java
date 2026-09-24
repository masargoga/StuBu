package com.stubu.specdriven.timetracking;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Timeline of one day's work periods. Every period is a row with a text description (readable by screen
 * readers) and a bar on a 24 hour scale; the bar starts and ends with a dot so check-in and check-out are
 * visibly connected, and an open period is drawn differently from a completed one.
 */
public class WorkTimeline extends Div {

    private static final int[] AXIS_HOURS = { 0, 4, 8, 12, 16, 20, 24 };

    public WorkTimeline() {
        addClassName("timeline");
    }

    /** Rebuilds the timeline for the given day. */
    public void show(DaySummary day, ZoneId zone, Instant now) {
        removeAll();
        Locale locale = getLocale();
        Instant dayStart = day.date().atStartOfDay(zone).toInstant();
        Instant dayEnd = day.date().plusDays(1).atStartOfDay(zone).toInstant();

        Div axis = new Div();
        axis.addClassName("timeline-axis");
        axis.getElement().setAttribute("aria-hidden", "true");
        for (int hour : AXIS_HOURS) {
            Span tick = new Span(String.format("%02d", hour));
            tick.addClassName("timeline-tick");
            tick.getStyle().set("left", percent(hour / 24.0));
            axis.add(tick);
        }

        Div rows = new Div();
        rows.addClassName("timeline-rows");
        rows.getElement().setAttribute("role", "list");
        rows.getElement().setAttribute("aria-label", getTranslation("time.timeline.label"));
        for (TimeEntry entry : day.entries()) {
            rows.add(row(entry, day, zone, now, dayStart, dayEnd, locale));
        }
        add(axis, rows);
    }

    private Div row(TimeEntry entry, DaySummary day, ZoneId zone, Instant now, Instant dayStart, Instant dayEnd,
            Locale locale) {
        String start = TimeFormats.timeOrDateTime(entry.getCheckInAt(), zone, locale, day.date());
        Duration duration = entry.durationAt(now);
        String description = entry.isActive()
                ? getTranslation("time.timeline.open", start, DurationFormat.format(duration))
                : getTranslation("time.timeline.completed", start,
                        TimeFormats.timeOrDateTime(entry.getCheckOutAt(), zone, locale, day.date()),
                        DurationFormat.format(duration));

        Span label = new Span(description);
        label.addClassName("timeline-label");

        Instant end = entry.isActive() ? now : entry.getCheckOutAt();
        double dayLength = Duration.between(dayStart, dayEnd).toMillis();
        double from = clamp(Duration.between(dayStart, entry.getCheckInAt()).toMillis() / dayLength);
        double to = clamp(Duration.between(dayStart, end).toMillis() / dayLength);

        Div bar = new Div();
        bar.addClassName("timeline-bar");
        bar.setClassName("timeline-bar-open", entry.isActive());
        bar.getStyle().set("left", percent(from)).set("width", percent(Math.max(to - from, 0)));
        Div track = new Div(bar);
        track.addClassName("timeline-track");
        track.getElement().setAttribute("aria-hidden", "true");

        Div row = new Div(label, track);
        row.addClassName("timeline-row");
        row.setClassName("timeline-row-open", entry.isActive());
        row.getElement().setAttribute("role", "listitem");
        return row;
    }

    private static double clamp(double fraction) {
        return Math.min(1, Math.max(0, fraction));
    }

    private static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.3f%%", fraction * 100);
    }
}
