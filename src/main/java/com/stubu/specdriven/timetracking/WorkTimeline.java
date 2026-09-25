package com.stubu.specdriven.timetracking;

import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.function.SerializableConsumer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

/**
 * Timeline of one day's work periods. Every period is a row with a text description (readable by screen
 * readers), its status and an edit control, and a bar on a 24 hour scale; the bar starts and ends with a dot
 * so check-in and check-out are visibly connected, and an open period is drawn differently from a completed
 * one.
 *
 * <p>Every row has Edit and Delete controls; they are disabled, with an explanation, once the timesheet of the
 * entry was submitted.
 */
public class WorkTimeline extends Div {

    private static final int[] AXIS_HOURS = { 0, 4, 8, 12, 16, 20, 24 };

    private boolean readOnly;
    private SerializableConsumer<Long> editHandler = id -> {
    };
    private SerializableConsumer<Long> deleteHandler = id -> {
    };

    public WorkTimeline() {
        addClassName("timeline");
    }

    /** A read-only timeline shows the periods without Edit and Delete, e.g. for a manager. */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /** What happens when the user chooses Edit or Delete on an entry; called with the entry's id. */
    public void setEntryActions(SerializableConsumer<Long> onEdit, SerializableConsumer<Long> onDelete) {
        this.editHandler = onEdit;
        this.deleteHandler = onDelete;
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
            tick.getStyle().set("left", DayTrack.percent(hour / 24.0));
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

        Badge status = new Badge(getTranslation(entry.isActive() ? "time.status.inProgress"
                : "time.status.completed"));
        status.setTestId("entry-status");
        if (!entry.isActive()) {
            status.addThemeVariants(BadgeVariant.SUCCESS);
        }

        boolean editable = day.isEditable(entry);
        Div header = readOnly ? new Div(label, status)
                : new Div(label, status, actions(entry.getId(), start, editable));
        header.addClassName("timeline-row-header");

        Div bar = DayTrack.bar(entry, dayStart, dayEnd, now);
        Div track = new Div(bar);
        track.addClassName("timeline-track");
        track.getElement().setAttribute("aria-hidden", "true");

        Div row = editable || readOnly ? new Div(header, track) : new Div(header, lockedNote(), track);
        row.addClassName("timeline-row");
        row.setClassName("timeline-row-open", entry.isActive());
        row.getElement().setAttribute("role", "listitem");
        return row;
    }

    /** Edit and Delete for one entry; disabled when the entry's timesheet does not allow corrections. */
    private Span actions(Long entryId, String start, boolean editable) {
        Button edit = new Button(getTranslation("time.edit"), VaadinIcon.EDIT.create());
        edit.setTestId("edit-entry");
        edit.getElement().setAttribute("aria-label", getTranslation("time.edit.label", start));
        edit.addClickListener(event -> editHandler.accept(entryId));

        Button delete = new Button(getTranslation("time.delete"), VaadinIcon.TRASH.create());
        delete.setTestId("delete-entry");
        delete.addThemeVariants(ButtonVariant.ERROR);
        delete.getElement().setAttribute("aria-label", getTranslation("time.delete.label", start));
        delete.addClickListener(event -> deleteHandler.accept(entryId));

        for (Button button : new Button[] { edit, delete }) {
            button.addThemeVariants(ButtonVariant.TERTIARY);
            button.setEnabled(editable);
        }
        Span actions = new Span(edit, delete);
        actions.addClassName("timeline-actions");
        return actions;
    }

    /** Why the entry cannot be corrected, shown right in its row. */
    private Div lockedNote() {
        var icon = VaadinIcon.LOCK.create();
        icon.getElement().setAttribute("aria-hidden", "true");
        Div note = new Div(icon, new Span(getTranslation("time.locked")));
        note.addClassName("timeline-locked");
        note.setTestId("entry-locked");
        return note;
    }

}
