package com.stubu.specdriven.timetracking;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
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
    private static final int REASON_MAX_LENGTH = 500;

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
    private final Div messageBox = new Div();
    private final Div loadErrorBox = new Div();
    private final Span loadError = new Span();
    private final Button retry = new Button();
    private final VerticalLayout day = new VerticalLayout();
    private final Span date = new Span();
    private final Span currentTime = new Span();
    private final Span elapsed = new Span();
    private final Div emptyHint = new Div();
    private final WorkTimeline timeline = new WorkTimeline();
    private final Span worked = new Span();
    private final Span breaks = new Span();
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
        actions.setWidthFull();

        messageBox.addClassName("time-message");
        messageBox.setVisible(false);

        var errorIcon = VaadinIcon.WARNING.create();
        errorIcon.getElement().setAttribute("aria-hidden", "true");
        retry.setTestId("retry");
        retry.addThemeVariants(ButtonVariant.PRIMARY);
        retry.addClickListener(event -> refresh());
        loadErrorBox.add(errorIcon, loadError, retry);
        loadErrorBox.addClassNames("time-message", "time-message-error", "time-load-error");
        loadErrorBox.getElement().setAttribute("role", "alert");
        loadErrorBox.setVisible(false);

        status.addClassName("time-status");
        status.getElement().setAttribute("role", "status");
        date.addClassName("time-date");
        currentTime.addClassName("time-current");
        currentTime.setTestId("current-time");
        elapsed.addClassName("time-elapsed");
        elapsed.setTestId("elapsed-time");
        Div clock = new Div(date, currentTime, elapsed);
        clock.addClassName("time-clock");
        emptyHint.addClassName("time-empty");
        worked.addClassName("time-total");
        worked.setTestId("total-worked");
        breaks.addClassName("time-total");
        Div totals = new Div(worked, breaks);
        totals.addClassName("time-totals");
        workedDetail.addClassName("time-total-detail");

        timeline.setEntryActions(this::openEditDialog, this::openDeleteDialog);
        day.setPadding(false);
        day.setSpacing(true);
        day.add(status, clock, emptyHint, timeline, totals, workedDetail);

        add(actions, messageBox, loadErrorBox, day);
        load();
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
        attachEvent.getUI().getPage().retrieveExtendedClientDetails(details -> {
            try {
                zone = ZoneId.of(details.getTimeZoneId());
                load();
                render();
            } catch (DateTimeException | NullPointerException unknownZone) {
                log.debug("Keeping the server time zone, the browser reported {}", details.getTimeZoneId());
            }
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
        Div error = new Div();
        error.addClassName("time-dialog-error");
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);

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
        Optional<TimeEntry> found = entryById(entryId);
        if (found.isEmpty()) {
            refresh();
            return;
        }
        TimeEntry entry = found.get();
        LocalDateTime in = entry.getCheckInAt().atZone(zone).toLocalDateTime();
        LocalDateTime out = entry.getCheckOutAt() == null ? null : entry.getCheckOutAt().atZone(zone).toLocalDateTime();

        Dialog dialog = new Dialog();
        dialog.setWidth(DIALOG_WIDTH);
        dialog.setHeaderTitle(getTranslation("time.edit.title"));

        // The date of an entry cannot change, so the check-in date is read-only.
        DatePicker inDate = new DatePicker(getTranslation("time.edit.checkInDate"), in.toLocalDate());
        inDate.setReadOnly(true);
        TimePicker inTime = timePicker(getTranslation("time.edit.checkInTime"), in.toLocalTime());
        // The check-out has its own date so that a period may end after midnight.
        DatePicker outDate = new DatePicker(getTranslation("time.edit.checkOutDate"),
                out == null ? null : out.toLocalDate());
        TimePicker outTime = timePicker(getTranslation("time.edit.checkOutTime"), out == null ? null : out.toLocalTime());
        TextField reason = new TextField(getTranslation("time.edit.reason"));
        reason.setMaxLength(REASON_MAX_LENGTH);
        reason.setWidthFull();
        Div error = errorBox();

        FormLayout form = new FormLayout(inDate, inTime, outDate, outTime, reason);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("28rem", 2));
        form.setColspan(reason, 2);
        VerticalLayout content = new VerticalLayout(form, error);
        content.setPadding(false);
        content.addClassName("time-dialog-content");
        dialog.add(content);

        Button cancel = new Button(getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("edit-cancel");
        cancel.addClassName("time-dialog-button");
        Button save = new Button(getTranslation("time.save"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.setTestId("edit-save");
        save.addClassName("time-dialog-button");
        save.addClickListener(event -> {
            if (inTime.isEmpty()) {
                showDialogError(error, getTranslation("time.edit.error.MISSING_START"));
                return;
            }
            boolean anyCheckOut = !outDate.isEmpty() || !outTime.isEmpty();
            if (anyCheckOut && (outDate.isEmpty() || outTime.isEmpty())) {
                showDialogError(error, getTranslation("time.edit.error.CHECK_OUT_INCOMPLETE"));
                return;
            }
            Instant newCheckIn = resolve(LocalDateTime.of(inDate.getValue(), inTime.getValue()),
                    entry.getCheckInAt());
            Instant newCheckOut = anyCheckOut
                    ? resolve(LocalDateTime.of(outDate.getValue(), outTime.getValue()), entry.getCheckOutAt())
                    : null;
            saveCorrection(dialog, error, entry.getId(), newCheckIn, newCheckOut, reason.getValue());
        });
        dialog.getFooter().add(cancel, save);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private TimePicker timePicker(String label, LocalTime value) {
        TimePicker picker = new TimePicker(label);
        picker.setStep(Duration.ofMinutes(1));
        picker.setValue(value == null ? null : value.truncatedTo(ChronoUnit.MINUTES));
        return picker;
    }

    /** Times are entered by the minute; a time the user did not touch keeps its exact recorded second. */
    private Instant resolve(LocalDateTime entered, Instant original) {
        Instant candidate = entered.atZone(zone).toInstant();
        return original != null && candidate.equals(original.truncatedTo(ChronoUnit.MINUTES)) ? original : candidate;
    }

    private void saveCorrection(Dialog dialog, Div error, long entryId, Instant checkIn, Instant checkOut,
            String reason) {
        try {
            service.correct(employeeId, entryId, checkIn, checkOut, reason, zone);
            dialog.close();
            show(new Message("time.edit.done", false));
        } catch (InvalidWorkPeriodException invalid) {
            showDialogError(error, getTranslation("time.edit.error." + invalid.getReason().name()));
            return;
        } catch (EntryLockedException locked) {
            dialog.close();
            show(new Message("time.locked", true));
        } catch (EntryNotFoundException gone) {
            dialog.close();
            show(new Message("time.entry.gone", true));
        } catch (DataAccessException e) {
            log.error("Correcting entry {} failed for employee {}", entryId, employeeId, e);
            showDialogError(error, getTranslation("time.edit.saveFailed"));
            return;
        }
        refresh();
    }

    private void openDeleteDialog(Long entryId) {
        Optional<TimeEntry> found = entryById(entryId);
        if (found.isEmpty()) {
            refresh();
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setWidth(DIALOG_WIDTH);
        dialog.setHeaderTitle(getTranslation("time.delete.title"));

        Paragraph question = new Paragraph(getTranslation("time.delete.text"));
        question.addClassName("time-dialog-text");
        TextField reason = new TextField(getTranslation("time.edit.reason"));
        reason.setMaxLength(REASON_MAX_LENGTH);
        reason.setWidthFull();
        Div error = errorBox();
        VerticalLayout content = new VerticalLayout(question, reason, error);
        content.setPadding(false);
        content.addClassName("time-dialog-content");
        dialog.add(content);

        Button cancel = new Button(getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("delete-cancel");
        cancel.addClassName("time-dialog-button");
        Button confirm = new Button(getTranslation("time.delete"));
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        confirm.setTestId("delete-confirm");
        confirm.addClassName("time-dialog-button");
        confirm.addClickListener(event -> deleteEntry(dialog, error, entryId, reason.getValue()));
        dialog.getFooter().add(cancel, confirm);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private void deleteEntry(Dialog dialog, Div error, long entryId, String reason) {
        try {
            service.delete(employeeId, entryId, reason, zone);
            dialog.close();
            show(new Message("time.delete.done", false));
        } catch (EntryLockedException locked) {
            dialog.close();
            show(new Message("time.locked", true));
        } catch (EntryNotFoundException gone) {
            dialog.close();
            show(new Message("time.entry.gone", true));
        } catch (DataAccessException e) {
            log.error("Deleting entry {} failed for employee {}", entryId, employeeId, e);
            showDialogError(error, getTranslation("time.edit.saveFailed")); // stays open: Delete again to retry
            return;
        }
        refresh();
    }

    private static Div errorBox() {
        Div error = new Div();
        error.addClassName("time-dialog-error");
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);
        return error;
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

        if (message == null) {
            messageBox.setVisible(false);
        } else {
            var icon = (message.error() ? VaadinIcon.WARNING : VaadinIcon.CHECK_CIRCLE).create();
            icon.getElement().setAttribute("aria-hidden", "true");
            messageBox.removeAll();
            messageBox.add(icon, new Span(getTranslation(message.key(), format(message.args(), locale))));
            messageBox.setClassName("time-message-error", message.error());
            messageBox.getElement().setAttribute("role", message.error() ? "alert" : "status");
            messageBox.setVisible(true);
        }

        loadError.setText(getTranslation("time.loadFailed"));
        retry.setText(getTranslation("time.retry"));
        loadErrorBox.setVisible(loadFailed);
        boolean showDay = !loadFailed && summary != null;
        day.setVisible(showDay);
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

        worked.setText(getTranslation("time.worked", DurationFormat.format(summary.worked())));
        breaks.setText(getTranslation("time.break", DurationFormat.format(summary.breaks())));
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
