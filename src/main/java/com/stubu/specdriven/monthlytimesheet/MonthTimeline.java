package com.stubu.specdriven.monthlytimesheet;

import com.stubu.specdriven.timetracking.DayTrack;
import com.stubu.specdriven.timetracking.DurationFormat;
import com.stubu.specdriven.timetracking.TimeEntry;
import com.stubu.specdriven.timetracking.TimeFormats;
import com.stubu.specdriven.timetracking.TimelineWindow;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.function.SerializableConsumer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * The timeline view of a monthly timesheet: one row per calendar day with the work periods of the day drawn on one
 * shared scale (see {@link TimelineWindow}), the daily total and break time, and weekends and public holidays marked with text (not only
 * colour). Work periods can be corrected or deleted while the timesheet is a draft.
 */
public class MonthTimeline extends Div {

    private boolean readOnly;
    private SerializableConsumer<LocalDate> dayHandler;
    private SerializableConsumer<Long> editHandler = id -> {
    };
    private SerializableConsumer<Long> deleteHandler = id -> {
    };

    public MonthTimeline() {
        addClassName("month-timeline");
    }

    /** A read-only timeline never offers Edit or Delete, whatever the status of the timesheet (manager review). */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    /** Adds a "day details" button to every day with work periods; called with the chosen day. */
    public void setDayDetails(SerializableConsumer<LocalDate> onDay) {
        this.dayHandler = onDay;
    }

    /** What happens when the user chooses Edit or Delete on a work period; called with the entry's id. */
    public void setEntryActions(SerializableConsumer<Long> onEdit, SerializableConsumer<Long> onDelete) {
        this.editHandler = onEdit;
        this.deleteHandler = onDelete;
    }

    public void show(MonthlyTimesheet sheet, ZoneId zone, Instant now, LocalDate today) {
        removeAll();
        Locale locale = getLocale();

        TimelineWindow window = sheet.days().stream()
                .map(line -> TimelineWindow.forDay(line.date(), line.day().entries(), zone, now))
                .reduce(TimelineWindow.DEFAULT, TimelineWindow::union);
        Div axis = new Div();
        axis.addClassName("month-axis");
        axis.getElement().setAttribute("aria-hidden", "true");
        for (int hour : window.ticks()) {
            Span tick = new Span(String.format("%02d", hour));
            tick.addClassName("timeline-tick");
            tick.getStyle().set("left", DayTrack.percent(window.fraction(hour)));
            axis.add(tick);
        }

        Div rows = new Div();
        rows.addClassName("month-rows");
        rows.getElement().setAttribute("role", "list");
        rows.getElement().setAttribute("aria-label", getTranslation("timesheet.timeline.label"));
        sheet.days().forEach(line -> rows.add(row(line, sheet.editable() && !readOnly, zone, now, today, locale, window)));
        add(axis, rows);
    }

    private Div row(DayLine line, boolean editable, ZoneId zone, Instant now, LocalDate today, Locale locale,
            TimelineWindow window) {
        LocalDate date = line.date();
        boolean hasEntries = !line.day().entries().isEmpty();

        Span label = new Span(date.format(DateTimeFormatter.ofPattern("EEE d", locale)));
        label.addClassName("day-label");
        Div header = new Div(label);
        header.addClassName("day-header");
        if (date.equals(today)) {
            header.add(tag(getTranslation("timesheet.today"), "day-today"));
        }
        if (line.weekend()) {
            header.add(tag(getTranslation("timesheet.weekend"), "day-weekend-tag"));
        }
        if (line.isHoliday()) {
            Badge holiday = new Badge(getTranslation("timesheet.holiday", line.holidayName()));
            holiday.addThemeVariants(BadgeVariant.WARNING);
            holiday.setTestId("holiday");
            header.add(holiday);
        }
        Span summary = new Span(hasEntries
                ? getTranslation("timesheet.day.summary", DurationFormat.format(line.day().worked()),
                        DurationFormat.format(line.day().breaks()))
                : getTranslation("timesheet.day.none"));
        summary.addClassName(hasEntries ? "day-summary" : "day-none");
        summary.setTestId("day-summary");
        header.add(summary);
        if (dayHandler != null && hasEntries) {
            Button details = new Button(VaadinIcon.EXPAND_FULL.create());
            details.setTestId("day-details");
            details.addThemeVariants(ButtonVariant.TERTIARY);
            details.getElement().setAttribute("aria-label", getTranslation("timesheet.day.details",
                    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))));
            details.setTooltipText(getTranslation("timesheet.day.detailsTooltip"));
            details.addClickListener(event -> dayHandler.accept(date));
            header.add(details);
        }

        Div row = new Div(header);
        row.addClassName("day-row");
        row.setClassName("day-weekend", line.weekend());
        row.setClassName("day-holiday", line.isHoliday());
        row.setClassName("day-current", date.equals(today));
        row.setClassName("day-empty", !hasEntries);
        row.getElement().setAttribute("role", "listitem");
        row.getElement().setAttribute("data-date", date.toString());
        row.getElement().setAttribute("aria-label", date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                .withLocale(locale)) + (line.weekend() ? ", " + getTranslation("timesheet.weekend") : "")
                + (line.isHoliday() ? ", " + getTranslation("timesheet.holiday", line.holidayName()) : ""));
        if (hasEntries) {
            row.add(DayTrack.forDay(line.day().entries(), date, zone, now, window));
            line.day().entries().forEach(entry -> row.add(period(entry, editable, zone, now, date, locale)));
        }
        return row;
    }

    private Div period(TimeEntry entry, boolean editable, ZoneId zone, Instant now, LocalDate date, Locale locale) {
        String start = TimeFormats.timeOrDateTime(entry.getCheckInAt(), zone, locale, date);
        String duration = DurationFormat.format(entry.durationAt(now));
        Span text = new Span(entry.isActive()
                ? getTranslation("time.timeline.open", start, duration)
                : getTranslation("time.timeline.completed", start,
                        TimeFormats.timeOrDateTime(entry.getCheckOutAt(), zone, locale, date), duration));
        text.addClassName("period-label");

        Div period = new Div(text);
        period.addClassName("period");
        if (editable) {
            period.add(iconButton(VaadinIcon.EDIT, "edit-entry", getTranslation("time.edit.label", start),
                    getTranslation("time.edit"), entry.getId(), false));
            period.add(iconButton(VaadinIcon.TRASH, "delete-entry", getTranslation("time.delete.label", start),
                    getTranslation("time.delete"), entry.getId(), true));
        }
        return period;
    }

    private Button iconButton(VaadinIcon icon, String testId, String ariaLabel, String tooltip, Long entryId,
            boolean delete) {
        Button button = new Button(icon.create());
        button.setTestId(testId);
        button.addThemeVariants(ButtonVariant.TERTIARY);
        if (delete) {
            button.addThemeVariants(ButtonVariant.ERROR);
        }
        button.getElement().setAttribute("aria-label", ariaLabel);
        button.setTooltipText(tooltip);
        button.addClickListener(event -> (delete ? deleteHandler : editHandler).accept(entryId));
        return button;
    }

    private static Span tag(String text, String className) {
        Span tag = new Span(text);
        tag.addClassNames("day-tag", className);
        return tag;
    }
}
