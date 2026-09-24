package com.stubu.specdriven.timetracking;

import com.vaadin.flow.component.AttachEvent;
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
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Locale;
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
    private ZoneId zone;
    private transient DaySummary summary;
    private Message message;

    private final Div status = new Div();
    private final Button checkIn = new Button();
    private final Button checkOut = new Button();
    private final Div messageBox = new Div();
    private final Span date = new Span();
    private final WorkTimeline timeline = new WorkTimeline();
    private final Span worked = new Span();
    private final Span breaks = new Span();

    public TimeTrackingPanel(TimeEntryService service, long employeeId) {
        this.service = service;
        this.employeeId = employeeId;
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
        status.addClassName("time-status");
        status.getElement().setAttribute("role", "status");
        date.addClassName("time-date");
        worked.addClassName("time-total");
        breaks.addClassName("time-total");
        Div totals = new Div(worked, breaks);
        totals.addClassName("time-totals");

        add(actions, messageBox, status, date, timeline, totals);
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
        dialog.add(content);

        Button cancel = new Button(getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("missing-cancel");
        Button confirm = new Button(getTranslation("time.confirm"));
        confirm.addThemeVariants(ButtonVariant.PRIMARY);
        confirm.setTestId("missing-confirm");
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

    // --- state and rendering ---------------------------------------------------------------------

    private void show(Message newMessage) {
        message = newMessage;
        render();
    }

    /** Reloads the day from the server and redraws the panel. */
    private void refresh() {
        load();
        render();
    }

    private void load() {
        try {
            summary = service.today(employeeId, zone);
        } catch (DataAccessException e) {
            log.error("Could not load the time entries of employee {}", employeeId, e);
            message = new Message("time.loadFailed", true);
        }
    }

    private void render() {
        Locale locale = getLocale();
        checkIn.setText(getTranslation("time.checkIn"));
        checkOut.setText(getTranslation("time.checkOut"));

        boolean working = summary != null && summary.open().isPresent();
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

        if (summary == null) {
            return;
        }
        status.setText(working
                ? getTranslation("time.status.working",
                        TimeFormats.time(summary.open().orElseThrow().getCheckInAt(), zone, locale))
                : getTranslation("time.status.notWorking"));
        status.setClassName("time-status-working", working);
        date.setText(TimeFormats.fullDate(summary.date(), locale));
        timeline.show(summary, zone, service.now());
        worked.setText(getTranslation("time.worked", DurationFormat.format(summary.worked())));
        breaks.setText(getTranslation("time.break", DurationFormat.format(summary.breaks())));
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
