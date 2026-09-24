package com.stubu.specdriven.timetracking;

import com.vaadin.flow.component.Component;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
        /** The timesheet was submitted in the meantime. */
        LOCKED,
        /** The entry does not exist any more. */
        GONE
    }

    private static final Logger log = LoggerFactory.getLogger(EntryCorrectionDialogs.class);
    private static final String DIALOG_WIDTH = "min(34rem, 92vw)";
    private static final int REASON_MAX_LENGTH = 500;

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
        picker.setStep(Duration.ofMinutes(1));
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
