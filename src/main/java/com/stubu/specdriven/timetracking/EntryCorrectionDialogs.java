package com.stubu.specdriven.timetracking;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.function.SerializableConsumer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * The dialogs to correct or delete a work period (UC-004), shared by every view that lists entries. Validation
 * problems and database errors are shown inside the dialog, which stays open; the caller is told what happened
 * once the dialog is done.
 */
public class EntryCorrectionDialogs {

    /** How a dialog ended, so the caller can show a message and reload. */
    public enum Outcome {
        UPDATED, DELETED,
        /** A work period was added afterwards (UC-015). */
        ADDED,
        /** The timesheet was submitted in the meantime. */
        LOCKED,
        /** The entry does not exist any more. */
        GONE
    }

    private static final Logger log = LoggerFactory.getLogger(EntryCorrectionDialogs.class);
    private static final String DIALOG_WIDTH = "min(34rem, 92vw)";
    private static final int REASON_MAX_LENGTH = 500;

    /**
     * The times offered in the list of a time field. The list only opens (by clicking the field or its clock icon)
     * for steps of 15 minutes or more; a step of one minute would hide it. Any minute can still be typed: the
     * field accepts values that do not line up with the step.
     */
    public static final Duration TIME_STEP = Duration.ofMinutes(15);

    private final TimeEntryService service;
    private final long employeeId;

    public EntryCorrectionDialogs(TimeEntryService service, long employeeId) {
        this.service = service;
        this.employeeId = employeeId;
    }

    /**
     * Opens the edit dialog for an entry.
     *
     * @param owner a component of the page, used for translations
     * @param done  called when the entry was saved or turned out to be locked or gone
     */
    public void openEdit(Component owner, TimeEntry entry, ZoneId zone, SerializableConsumer<Outcome> done) {
        LocalDateTime in = entry.getCheckInAt().atZone(zone).toLocalDateTime();
        LocalDateTime out = entry.getCheckOutAt() == null ? null : entry.getCheckOutAt().atZone(zone).toLocalDateTime();

        Dialog dialog = new Dialog();
        dialog.setWidth(DIALOG_WIDTH);
        dialog.setHeaderTitle(owner.getTranslation("time.edit.title"));

        // The date of an entry cannot change, so the check-in date is read-only.
        DatePicker inDate = new DatePicker(owner.getTranslation("time.edit.checkInDate"), in.toLocalDate());
        inDate.setReadOnly(true);
        TimePicker inTime = timePicker(owner.getTranslation("time.edit.checkInTime"), in.toLocalTime());
        // The check-out has its own date so that a period may end after midnight.
        DatePicker outDate = new DatePicker(owner.getTranslation("time.edit.checkOutDate"),
                out == null ? null : out.toLocalDate());
        TimePicker outTime = timePicker(owner.getTranslation("time.edit.checkOutTime"),
                out == null ? null : out.toLocalTime());
        TextField reason = reasonField(owner);
        Div error = errorBox();

        FormLayout form = new FormLayout(inDate, inTime, outDate, outTime, reason);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("28rem", 2));
        form.setColspan(reason, 2);
        VerticalLayout content = new VerticalLayout(form, error);
        content.setPadding(false);
        content.addClassName("time-dialog-content");
        dialog.add(content);

        Button cancel = new Button(owner.getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("edit-cancel");
        cancel.addClassName("time-dialog-button");
        Button save = new Button(owner.getTranslation("time.save"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.setTestId("edit-save");
        save.addClassName("time-dialog-button");
        save.addClickListener(event -> {
            if (inTime.isEmpty()) {
                showError(error, owner.getTranslation("time.edit.error.MISSING_START"));
                return;
            }
            boolean anyCheckOut = !outDate.isEmpty() || !outTime.isEmpty();
            if (anyCheckOut && (outDate.isEmpty() || outTime.isEmpty())) {
                showError(error, owner.getTranslation("time.edit.error.CHECK_OUT_INCOMPLETE"));
                return;
            }
            Instant newCheckIn = resolve(LocalDateTime.of(inDate.getValue(), inTime.getValue()),
                    entry.getCheckInAt(), zone);
            Instant newCheckOut = anyCheckOut
                    ? resolve(LocalDateTime.of(outDate.getValue(), outTime.getValue()), entry.getCheckOutAt(), zone)
                    : null;
            correct(owner, dialog, error, entry.getId(), newCheckIn, newCheckOut, reason.getValue(), zone, done);
        });
        dialog.getFooter().add(cancel, save);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    /**
     * Opens the dialog to add a work period afterwards (UC-015): for today or an earlier day. "Save and add another"
     * keeps the dialog open with the same date; every saved entry is reported to {@code done} as ADDED.
     *
     * @param presetDate the date to start with, or {@code null} to let the employee choose
     */
    public void openAdd(Component owner, LocalDate presetDate, ZoneId zone, SerializableConsumer<Outcome> done) {
        LocalDate today = service.currentDate(zone);
        Dialog dialog = new Dialog();
        dialog.setWidth(DIALOG_WIDTH);
        dialog.setHeaderTitle(owner.getTranslation("time.add.title"));

        DatePicker date = datePicker(owner, owner.getTranslation("time.add.date"), presetDate, today, "add-date");
        TimePicker inTime = timePicker(owner.getTranslation("time.edit.checkInTime"), null);
        inTime.setTestId("add-check-in");
        inTime.setRequiredIndicatorVisible(true);
        TimePicker outTime = timePicker(owner.getTranslation("time.edit.checkOutTime"), null);
        outTime.setTestId("add-check-out");
        outTime.setRequiredIndicatorVisible(true);
        // The check-out is on the same day unless the employee says otherwise: it may be after midnight.
        DatePicker outDate = datePicker(owner, owner.getTranslation("time.edit.checkOutDate"), presetDate, today,
                "add-check-out-date");
        date.addValueChangeListener(event -> {
            if (outDate.isEmpty() || Objects.equals(outDate.getValue(), event.getOldValue())) {
                outDate.setValue(event.getValue());
            }
        });
        TextField reason = reasonField(owner);
        reason.setLabel(owner.getTranslation("time.add.reason"));
        reason.setTestId("add-reason");
        Div error = errorBox();
        Div added = new Div();
        added.addClassName("time-dialog-success");
        added.getElement().setAttribute("role", "status");
        added.setTestId("add-success");
        added.setText(owner.getTranslation("time.add.done"));
        added.setVisible(false);

        FormLayout form = new FormLayout(date, inTime, outTime, outDate, reason);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("28rem", 2));
        form.setColspan(date, 2);
        form.setColspan(reason, 2);
        VerticalLayout content = new VerticalLayout(added, form, error);
        content.setPadding(false);
        content.addClassName("time-dialog-content");
        dialog.add(content);

        SerializableConsumer<Boolean> save = another -> {
            error.setVisible(false);
            added.setVisible(false);
            if (date.isEmpty() || inTime.isEmpty() || outTime.isEmpty()) {
                showError(error, owner.getTranslation("time.add.error.REQUIRED"));
                return;
            }
            LocalDate endDay = outDate.isEmpty() ? date.getValue() : outDate.getValue();
            Instant checkIn = LocalDateTime.of(date.getValue(), inTime.getValue()).atZone(zone).toInstant();
            Instant checkOut = LocalDateTime.of(endDay, outTime.getValue()).atZone(zone).toInstant();
            try {
                service.add(employeeId, checkIn, checkOut, reason.getValue(), zone);
            } catch (InvalidWorkPeriodException invalid) {
                showError(error, owner.getTranslation("time.edit.error." + invalid.getReason().name()));
                return;
            } catch (EntryLockedException locked) {
                dialog.close();
                done.accept(Outcome.LOCKED);
                return;
            } catch (DataAccessException e) {
                log.error("Adding a work period failed for employee {}", employeeId, e);
                showError(error, owner.getTranslation("time.edit.saveFailed"));
                return;
            }
            if (another) {
                inTime.clear();
                outTime.clear();
                inTime.setInvalid(false); // an empty field is not an error yet, the employee is about to fill it
                outTime.setInvalid(false);
                reason.clear();
                outDate.setValue(date.getValue());
                added.setVisible(true);
            } else {
                dialog.close();
            }
            done.accept(Outcome.ADDED);
        };

        Button cancel = new Button(owner.getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("add-cancel");
        cancel.addClassName("time-dialog-button");
        Button saveAnother = new Button(owner.getTranslation("time.add.saveAnother"), event -> save.accept(true));
        saveAnother.setTestId("add-save-another");
        saveAnother.addClassName("time-dialog-button");
        Button saveButton = new Button(owner.getTranslation("time.save"), event -> save.accept(false));
        saveButton.addThemeVariants(ButtonVariant.PRIMARY);
        saveButton.setTestId("add-save");
        saveButton.addClassName("time-dialog-button");
        dialog.getFooter().add(cancel, saveAnother, saveButton);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private static DatePicker datePicker(Component owner, String label, LocalDate value, LocalDate max, String testId) {
        DatePicker picker = new DatePicker(label, value);
        picker.setLocale(owner.getUI().map(UI::getLocale).orElse(Locale.ENGLISH)); // month and weekday names and the date format follow the language of the page
        picker.setMax(max);
        picker.setRequiredIndicatorVisible(true);
        picker.setTestId(testId);
        return picker;
    }

    /** Opens the confirmation dialog to delete an entry. */
    public void openDelete(Component owner, TimeEntry entry, ZoneId zone, SerializableConsumer<Outcome> done) {
        Dialog dialog = new Dialog();
        dialog.setWidth(DIALOG_WIDTH);
        dialog.setHeaderTitle(owner.getTranslation("time.delete.title"));

        Paragraph question = new Paragraph(owner.getTranslation("time.delete.text"));
        question.addClassName("time-dialog-text");
        TextField reason = reasonField(owner);
        Div error = errorBox();
        VerticalLayout content = new VerticalLayout(question, reason, error);
        content.setPadding(false);
        content.addClassName("time-dialog-content");
        dialog.add(content);

        Button cancel = new Button(owner.getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("delete-cancel");
        cancel.addClassName("time-dialog-button");
        Button confirm = new Button(owner.getTranslation("time.delete"));
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        confirm.setTestId("delete-confirm");
        confirm.addClassName("time-dialog-button");
        long entryId = entry.getId();
        confirm.addClickListener(event -> delete(owner, dialog, error, entryId, reason.getValue(), zone, done));
        dialog.getFooter().add(cancel, confirm);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private void correct(Component owner, Dialog dialog, Div error, long entryId, Instant checkIn,
            Instant checkOut, String reason, ZoneId zone, SerializableConsumer<Outcome> done) {
        Outcome outcome;
        try {
            service.correct(employeeId, entryId, checkIn, checkOut, reason, zone);
            outcome = Outcome.UPDATED;
        } catch (InvalidWorkPeriodException invalid) {
            showError(error, owner.getTranslation("time.edit.error." + invalid.getReason().name()));
            return;
        } catch (EntryLockedException locked) {
            outcome = Outcome.LOCKED;
        } catch (EntryNotFoundException gone) {
            outcome = Outcome.GONE;
        } catch (DataAccessException e) {
            log.error("Correcting entry {} failed for employee {}", entryId, employeeId, e);
            showError(error, owner.getTranslation("time.edit.saveFailed"));
            return;
        }
        dialog.close();
        done.accept(outcome);
    }

    private void delete(Component owner, Dialog dialog, Div error, long entryId, String reason, ZoneId zone,
            SerializableConsumer<Outcome> done) {
        Outcome outcome;
        try {
            service.delete(employeeId, entryId, reason, zone);
            outcome = Outcome.DELETED;
        } catch (EntryLockedException locked) {
            outcome = Outcome.LOCKED;
        } catch (EntryNotFoundException gone) {
            outcome = Outcome.GONE;
        } catch (DataAccessException e) {
            log.error("Deleting entry {} failed for employee {}", entryId, employeeId, e);
            showError(error, owner.getTranslation("time.edit.saveFailed")); // stays open: Delete again to retry
            return;
        }
        dialog.close();
        done.accept(outcome);
    }

    private static TimePicker timePicker(String label, LocalTime value) {
        TimePicker picker = new TimePicker(label);
        picker.setStep(TIME_STEP);
        picker.setValue(value == null ? null : value.truncatedTo(ChronoUnit.MINUTES));
        return picker;
    }

    private static TextField reasonField(Component owner) {
        TextField reason = new TextField(owner.getTranslation("time.edit.reason"));
        reason.setMaxLength(REASON_MAX_LENGTH);
        reason.setWidthFull();
        return reason;
    }

    /** Times are entered by the minute; a time the user did not touch keeps its exact recorded second. */
    private static Instant resolve(LocalDateTime entered, Instant original, ZoneId zone) {
        Instant candidate = entered.atZone(zone).toInstant();
        return original != null && candidate.equals(original.truncatedTo(ChronoUnit.MINUTES)) ? original : candidate;
    }

    private static Div errorBox() {
        Div error = new Div();
        error.addClassName("time-dialog-error");
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);
        return error;
    }

    private static void showError(Div error, String text) {
        error.setText(text);
        error.setVisible(true);
    }
}
