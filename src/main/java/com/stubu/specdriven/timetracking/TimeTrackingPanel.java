package com.stubu.specdriven.timetracking;

import com.stubu.specdriven.base.BrowserTimeZone;
import com.stubu.specdriven.base.DialogError;
import com.stubu.specdriven.base.LoadErrorBox;
import com.stubu.specdriven.base.MessageBox;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.shared.Registration;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * The employee's time recording panel: check-in and check-out buttons, the current status, a message about
 * the last action, today's timeline and the day's worked and break time.
 *
 * <p>The server decides what a click means. Both buttons stay available; when the state on the server
 * differs from what the panel shows (another tab or device), the employee is asked what to do instead of
 * getting an error.
 */
public class TimeTrackingPanel extends VerticalLayout implements LocaleChangeObserver {

    private static final Logger log = LoggerFactory.getLogger(TimeTrackingPanel.class);
    private static final String DIALOG_WIDTH = "min(34rem, 92vw)";

    /** The last message shown to the user; kept as key and arguments so it can be translated again. */
    private record Message(String key, boolean error, Object... args) {
    }

    private final transient TimeEntryService service;
    private final long employeeId;
    private final Duration refreshInterval;
    private ZoneId zone;
    private transient DaySummary summary;
    private boolean loadFailed;
    private Message message;
    private String renderedSignature = "";
    private Registration refreshTimer;

    private final Div status = new Div();
    private final Button checkIn = new Button();
    private final Button checkOut = new Button();
    private final MessageBox messageBox = new MessageBox();
    private final LoadErrorBox loadErrorBox = new LoadErrorBox(this::refresh);
    private final VerticalLayout day = new VerticalLayout();
    private final Span date = new Span();
    private final Span currentTime = new Span();
    private final Span elapsed = new Span();
    private final Div emptyHint = new Div();
    private final WorkTimeline timeline = new WorkTimeline();
    private final Span worked = new Span();
    private final Span breaks = new Span();
    private final Span firstIn = new Span();
    private final Div heroText = new Div();
    private final Span dayHeading = new Span();
    private final Div workedDetail = new Div();

    /**
     * @param refreshInterval how often the panel reloads itself while it is open, so the current time, the
     *                        elapsed time and the totals stay correct
     */
    public TimeTrackingPanel(TimeEntryService service, long employeeId, Duration refreshInterval) {
        this.service = service;
        this.employeeId = employeeId;
        this.refreshInterval = refreshInterval;
        this.zone = service.defaultZone();

        addClassName("time-tracking");
        setPadding(false);
        setSpacing(true);

        configureAction(checkIn, "check-in", VaadinIcon.SIGN_IN);
        configureAction(checkOut, "check-out", VaadinIcon.SIGN_OUT);
        checkIn.addClickListener(event -> checkIn());
        checkOut.addClickListener(event -> checkOut());
        HorizontalLayout actions = new HorizontalLayout(checkIn, checkOut);
        actions.addClassName("time-actions");

        status.addClassName("time-status");
        status.getElement().setAttribute("role", "status");
        date.addClassName("time-date");
        currentTime.addClassName("time-current");
        currentTime.setTestId("current-time");
        elapsed.addClassName("time-elapsed");
        elapsed.setTestId("elapsed-time");
        Div clock = new Div(date, currentTime, elapsed);
        clock.addClassName("time-clock");
        heroText.add(status, clock);
        heroText.addClassName("time-hero-text");
        Div hero = new Div(heroText, actions);
        hero.addClassName("time-hero");
        emptyHint.addClassName("time-empty");
        worked.addClassNames("time-total", "time-tile");
        worked.setTestId("total-worked");
        breaks.addClassNames("time-total", "time-tile");
        firstIn.addClassNames("time-total", "time-tile");
        firstIn.setTestId("first-check-in");
        Div totals = new Div(worked, breaks, firstIn);
        totals.addClassName("time-totals");
        dayHeading.addClassName("time-card-title");
        Div timelineCard = new Div(dayHeading, emptyHint, timeline);
        timelineCard.addClassNames("time-card", "time-day-card");
        workedDetail.addClassName("time-total-detail");

        timeline.setEntryActions(this::openEditDialog, this::openDeleteDialog);
        day.setPadding(false);
        day.setSpacing(true);
        day.add(totals, timelineCard, workedDetail);

        add(hero, messageBox, loadErrorBox, day);
        load();
    }

    /** A summary tile: a small label above a large value. */
    private static void tile(Span tile, String label, String value) {
        Span labelText = new Span(label + " "); // the space keeps "label value" readable as one text
        labelText.addClassName("time-tile-label");
        Span valueText = new Span(value);
        valueText.addClassName("time-tile-value");
        tile.removeAll();
        tile.add(labelText, valueText);
    }

    private static void configureAction(Button button, String testId, VaadinIcon icon) {
        button.setIcon(icon.create());
        button.setTestId(testId);
        button.addClassName("time-action");
    }

    /** The browser's time zone is used to decide what "today" is and to present times. */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        startRefreshTimer(attachEvent.getUI());
        BrowserTimeZone.detect(attachEvent.getUI(), null, browserZone -> {
            zone = browserZone;
            load();
            render();
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopRefreshTimer();
    }

    private void startRefreshTimer(UI ui) {
        stopRefreshTimer();
        refreshTimer = ui.triggerAfter(refreshInterval, () -> {
            tick();
            startRefreshTimer(ui);
        });
    }

    private void stopRefreshTimer() {
        if (refreshTimer != null) {
            refreshTimer.remove();
            refreshTimer = null;
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    // --- actions ---------------------------------------------------------------------------------

    private void checkIn() {
        try {
            TimeEntry entry = service.checkIn(employeeId);
            show(new Message("time.checkedIn", false, entry.getCheckInAt()));
        } catch (AlreadyCheckedInException alreadyCheckedIn) {
            openReplaceDialog(alreadyCheckedIn.getSince());
            return;
        } catch (DataAccessException e) {
            log.error("Check-in failed for employee {}", employeeId, e);
            show(new Message("time.checkInFailed", true));
            return;
        }
        refresh();
    }

    private void checkOut() {
        try {
            TimeEntry entry = service.checkOut(employeeId);
            show(checkedOut(entry));
        } catch (NotCheckedInException notCheckedIn) {
            openMissingCheckInDialog();
            return;
        } catch (DataAccessException e) {
            log.error("Check-out failed for employee {}", employeeId, e);
            show(new Message("time.checkOutFailed", true));
            return;
        }
        refresh();
    }

    private void replaceCheckIn() {
        try {
            TimeEntry entry = service.replaceCheckIn(employeeId);
            show(new Message("time.replaced", false, entry.getCheckInAt()));
        } catch (NotCheckedInException notCheckedIn) {
            show(new Message("time.notCheckedIn", true));
        } catch (DataAccessException e) {
            log.error("Replacing the check-in failed for employee {}", employeeId, e);
            show(new Message("time.replaceFailed", true));
            return;
        }
        refresh();
    }

    private static Message checkedOut(TimeEntry entry) {
        return new Message("time.checkedOut", false, entry.getCheckOutAt(),
                entry.durationAt(entry.getCheckOutAt()));
    }

    // --- dialogs ---------------------------------------------------------------------------------

    private void openReplaceDialog(Instant since) {
        Locale locale = getLocale();
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setWidth(DIALOG_WIDTH);
        dialog.setHeader(getTranslation("time.replace.title"));
        dialog.setText(getTranslation("time.replace.text", TimeFormats.time(since, zone, locale)));
        dialog.setCancelable(true);
        dialog.setCancelText(getTranslation("time.cancel"));
        dialog.setConfirmText(getTranslation("time.replace.confirm"));
        dialog.addConfirmListener(event -> replaceCheckIn());
        // Detach only after the closing animation: the confirm event of a button click can arrive after "closed".
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private void openMissingCheckInDialog() {
        Dialog dialog = new Dialog();
        dialog.setWidth(DIALOG_WIDTH);
        dialog.setHeaderTitle(getTranslation("time.missing.title"));

        DatePicker startDate = new DatePicker(getTranslation("time.missing.date"));
        startDate.setValue(service.currentDate(zone));
        startDate.setRequired(true);
        TimePicker startTime = new TimePicker(getTranslation("time.missing.time"));
        startTime.setRequired(true);
        startTime.setStep(EntryCorrectionDialogs.TIME_STEP); // a list of times to choose from; any minute can be typed
        DialogError error = new DialogError();

        HorizontalLayout fields = new HorizontalLayout(startDate, startTime);
        fields.addClassName("time-dialog-fields");
        Paragraph question = new Paragraph(getTranslation("time.missing.text"));
        question.addClassName("time-dialog-text");
        VerticalLayout content = new VerticalLayout(question, fields, error);
        content.setPadding(false);
        content.addClassName("time-dialog-content");
        dialog.add(content);

        Button cancel = new Button(getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("missing-cancel");
        cancel.addClassName("time-dialog-button");
        Button confirm = new Button(getTranslation("time.confirm"));
        confirm.addThemeVariants(ButtonVariant.PRIMARY);
        confirm.setTestId("missing-confirm");
        confirm.addClassName("time-dialog-button");
        confirm.addClickListener(event -> {
            if (startDate.isEmpty() || startTime.isEmpty()) {
                showDialogError(error, getTranslation("time.missing.required"));
                return;
            }
            Instant start = LocalDateTime.of(startDate.getValue(), startTime.getValue()).atZone(zone).toInstant();
            confirmMissingCheckIn(dialog, error, start);
        });
        dialog.getFooter().add(cancel, confirm);
        // Detach only after the closing animation: the confirm event of a button click can arrive after "closed".
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private void confirmMissingCheckIn(Dialog dialog, Div error, Instant start) {
        try {
            TimeEntry entry = service.checkOutWithMissingCheckIn(employeeId, start);
            dialog.close();
            show(checkedOut(entry));
        } catch (InvalidWorkPeriodException invalid) {
            showDialogError(error, getTranslation("time.missing.error." + invalid.getReason().name()));
            return;
        } catch (AlreadyCheckedInException alreadyCheckedIn) {
            dialog.close();
            show(new Message("time.alreadyCheckedIn", true, alreadyCheckedIn.getSince()));
        } catch (DataAccessException e) {
            log.error("Check-out without check-in failed for employee {}", employeeId, e);
            showDialogError(error, getTranslation("time.checkOutFailed"));
            return;
        }
        refresh();
    }

    private static void showDialogError(Div error, String text) {
        error.setText(text);
        error.setVisible(true);
    }

    // --- correcting and deleting entries -----------------------------------------------------------

    private Optional<TimeEntry> entryById(long entryId) {
        return summary == null ? Optional.empty()
                : summary.entries().stream().filter(entry -> entry.getId() == entryId).findFirst();
    }

    private void openEditDialog(Long entryId) {
        entryById(entryId).ifPresentOrElse(
                entry -> corrections().openEdit(this, entry, zone, this::corrected), this::refresh);
    }

    private void openDeleteDialog(Long entryId) {
        entryById(entryId).ifPresentOrElse(
                entry -> corrections().openDelete(this, entry, zone, this::corrected), this::refresh);
    }

    private EntryCorrectionDialogs corrections() {
        return new EntryCorrectionDialogs(service, employeeId);
    }

    private void corrected(EntryCorrectionDialogs.Outcome outcome) {
        switch (outcome) {
            case UPDATED -> show(new Message("time.edit.done", false));
            case DELETED -> show(new Message("time.delete.done", false));
            case LOCKED -> show(new Message("time.locked", true));
            case GONE -> show(new Message("time.entry.gone", true));
        }
        refresh();
    }

    // --- state and rendering ---------------------------------------------------------------------

    private void show(Message newMessage) {
        message = newMessage;
        render();
    }

    /** Reloads the day from the server and redraws the panel. */
    public void refresh() {
        load();
        render();
    }

    private void load() {
        try {
            summary = service.today(employeeId, zone);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load the time entries of employee {}", employeeId, e);
            loadFailed = true;
        }
    }

    /** Called by the timer: redraws only when something visible changed (an entry, or the minute). */
    private void tick() {
        load();
        if (!signature().equals(renderedSignature)) {
            render();
        }
    }

    private String signature() {
        StringBuilder signature = new StringBuilder().append(loadFailed).append(zone)
                .append(service.now().truncatedTo(ChronoUnit.MINUTES));
        if (summary != null) {
            summary.entries().forEach(entry -> signature.append('|').append(entry.getId()).append(',')
                    .append(entry.getCheckInAt()).append(',').append(entry.getCheckOutAt()).append(',')
                    .append(summary.isEditable(entry)));
        }
        return signature.toString();
    }

    private void render() {
        Locale locale = getLocale();
        renderedSignature = signature();
        checkIn.setText(getTranslation("time.checkIn"));
        checkOut.setText(getTranslation("time.checkOut"));

        boolean working = !loadFailed && summary != null && summary.open().isPresent();
        checkIn.setThemeVariant(ButtonVariant.PRIMARY, !working);
        checkOut.setThemeVariant(ButtonVariant.PRIMARY, working);

        messageBox.show(message == null ? null : getTranslation(message.key(), format(message.args(), locale)), message != null && message.error());

        loadErrorBox.update(loadFailed, getTranslation("time.loadFailed"), getTranslation("time.retry"));
        boolean showDay = !loadFailed && summary != null;
        day.setVisible(showDay);
        heroText.setVisible(showDay);
        if (!showDay) {
            return;
        }

        Instant now = service.now();
        status.setText(working
                ? getTranslation("time.status.working",
                        TimeFormats.time(summary.open().orElseThrow().getCheckInAt(), zone, locale))
                : getTranslation("time.status.notWorking"));
        status.setClassName("time-status-working", working);
        date.setText(TimeFormats.fullDate(summary.date(), locale));
        currentTime.setText(getTranslation("time.currentTime", TimeFormats.time(now, zone, locale)));
        elapsed.setVisible(working);
        elapsed.setText(getTranslation("time.elapsed", DurationFormat.format(summary.openElapsed())));

        boolean empty = summary.entries().isEmpty();
        emptyHint.setText(getTranslation("time.empty"));
        emptyHint.setVisible(empty);
        timeline.show(summary, zone, now);

        dayHeading.setText(getTranslation("time.day.title"));
        tile(worked, getTranslation("time.worked.label"), DurationFormat.format(summary.worked()));
        tile(breaks, getTranslation("time.break.label"), DurationFormat.format(summary.breaks()));
        firstIn.setVisible(!empty);
        if (!empty) {
            tile(firstIn, getTranslation("time.firstIn.label"), TimeFormats.time(summary.entries().get(0).getCheckInAt(), zone, locale));
        }
        workedDetail.setVisible(working);
        workedDetail.setText(getTranslation("time.worked.detail", DurationFormat.format(summary.completed()),
                DurationFormat.format(summary.openElapsed())));
    }

    /** Turns instants and durations in message arguments into text in the user's zone and locale. */
    private Object[] format(Object[] args, Locale locale) {
        return Arrays.stream(args).map(arg -> {
            if (arg instanceof Instant instant) {
                return TimeFormats.time(instant, zone, locale);
            }
            if (arg instanceof Duration duration) {
                return DurationFormat.format(duration);
            }
            return arg;
        }).toArray();
    }
}
