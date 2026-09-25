package com.stubu.specdriven.usecases.uc004_correct_historical_time_entries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditLogEntry;
import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.home.HomeView;
import com.stubu.specdriven.security.LoginView;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.EntryLockedException;
import com.stubu.specdriven.timetracking.EntryNotFoundException;
import com.stubu.specdriven.timetracking.InvalidWorkPeriodException;
import com.stubu.specdriven.timetracking.TimeEntry;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.stubu.specdriven.timetracking.TimeTrackingPanel;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-004 Correct Historical Time Entries. The panel's edit and delete dialogs are driven browserless; the test
 * clock says it is 2026-09-23 17:00 UTC (the panel shows that day in UTC), and work periods are recorded through
 * the service. Timesheet status is set directly, as UC-006 will do later.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee
class UC004CorrectHistoricalTimeEntries extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-09-23T17:00:00Z");
    private static final ZoneOffset UTC = ZoneOffset.UTC;
    private static final String ALICE = "alice.employee@example.com";

    @Autowired
    MutableClock clock;
    @Autowired
    TimeEntryService service;
    @MockitoSpyBean
    TimeEntryRepository timeEntries;
    @Autowired
    TimesheetRepository timesheets;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    JdbcTemplate jdbc;

    private long alice;
    private int auditBefore;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(timeEntries);
        clock.set(NOW);
        jdbc.update("delete from time_entry");
        jdbc.update("delete from timesheet");
        alice = employees.findByEmailIgnoreCase(ALICE).orElseThrow().getId();
        auditBefore = auditLog.findAllByOrderByIdAsc().size();
    }

    @AfterEach
    void resetRepository() {
        Mockito.reset(timeEntries);
    }

    // --- Main Flow: Edit ------------------------------------------------------------------------

    @Test
    void mainFlow_edit_changesTheTimesAndAuditsOldAndNewValuesWithTheReason() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        auditBefore = auditLog.findAllByOrderByIdAsc().size(); // only the correction is counted
        TimeTrackingPanel panel = openPanel();

        test(button(panel, "edit-entry", 0)).click();

        Dialog dialog = find(Dialog.class).single();
        assertTrue(dialog.isOpened());
        DatePicker checkInDate = datePickers().get(0);
        assertTrue(checkInDate.isReadOnly(), "The date of an entry cannot be changed");
        assertEquals(LocalDate.of(2026, 9, 23), checkInDate.getValue());
        assertEquals(LocalTime.of(8, 0), timePickers().get(0).getValue());
        assertEquals(LocalTime.of(12, 0), timePickers().get(1).getValue());
        assertEquals(LocalDate.of(2026, 9, 23), datePickers().get(1).getValue());

        timePickers().get(0).setValue(LocalTime.of(8, 30));
        timePickers().get(1).setValue(LocalTime.of(12, 15));
        reasonField().setValue("Forgot to check in on time");
        test(button(dialog, "edit-save")).click();

        TimeEntry stored = timeEntries.findById(entry.getId()).orElseThrow();
        assertEquals(Instant.parse("2026-09-23T08:30:00Z"), stored.getCheckInAt());
        assertEquals(Instant.parse("2026-09-23T12:15:00Z"), stored.getCheckOutAt());
        assertFalse(dialog.isOpened());
        assertTrue(text(panel).contains("Time entry updated."), text(panel));
        assertEquals(List.of("8:30 AM – 12:15 PM, 3h 45m"), labels(panel));
        assertTrue(text(panel).contains("Total hours today: 3h 45m"), text(panel));
        AuditLogEntry audit = onlyNewAuditEntry();
        assertEquals(AuditAction.UPDATE, audit.getAction());
        assertEquals(alice, audit.getUserId());
        assertEquals(entry.getId(), audit.getEntityId());
        assertTrue(audit.getOldValues().contains("2026-09-23T08:00:00Z"), audit.getOldValues());
        assertTrue(audit.getNewValues().contains("2026-09-23T08:30:00Z"), audit.getNewValues());
        assertTrue(audit.getNewValues().contains("2026-09-23T12:15:00Z"), audit.getNewValues());
        assertEquals("Forgot to check in on time", audit.getReason());
    }

    @Test
    void mainFlow_edit_timesThatWereNotTouchedKeepTheirRecordedSeconds() {
        clock.set(Instant.parse("2026-09-23T08:03:14Z"));
        service.checkIn(alice);
        clock.set(Instant.parse("2026-09-23T12:07:41Z"));
        TimeEntry entry = service.checkOut(alice);
        clock.set(NOW);
        TimeTrackingPanel panel = openPanel();

        test(button(panel, "edit-entry", 0)).click();
        timePickers().get(1).setValue(LocalTime.of(12, 30)); // only the check-out is changed
        test(button(find(Dialog.class).single(), "edit-save")).click();

        TimeEntry stored = timeEntries.findById(entry.getId()).orElseThrow();
        assertEquals(Instant.parse("2026-09-23T08:03:14Z"), stored.getCheckInAt());
        assertEquals(Instant.parse("2026-09-23T12:30:00Z"), stored.getCheckOutAt());
    }

    @Test
    void mainFlow_edit_cancelChangesNothing() {
        record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "edit-entry", 0)).click();
        timePickers().get(0).setValue(LocalTime.of(9, 0));

        test(button(find(Dialog.class).single(), "edit-cancel")).click();

        assertEquals(Instant.parse("2026-09-23T08:00:00Z"), timeEntries.findAll().getFirst().getCheckInAt());
        assertEquals(auditBefore + 1, auditLog.findAllByOrderByIdAsc().size(), "Only the original recording");
    }

    @Test
    void mainFlow_edit_anOpenEntryCanKeepRunningOrBeClosed() {
        clock.set(Instant.parse("2026-09-23T09:00:00Z"));
        TimeEntry open = service.checkIn(alice);
        clock.set(NOW);
        TimeTrackingPanel panel = openPanel();

        test(button(panel, "edit-entry", 0)).click();
        assertNull(datePickers().get(1).getValue(), "No check-out yet");
        timePickers().get(0).setValue(LocalTime.of(8, 45)); // only the check-in is corrected
        test(button(find(Dialog.class).single(), "edit-save")).click();
        TimeEntry corrected = timeEntries.findById(open.getId()).orElseThrow();
        assertEquals(Instant.parse("2026-09-23T08:45:00Z"), corrected.getCheckInAt());
        assertTrue(corrected.isActive(), "Still open");
        assertEquals(alice, corrected.getOpenEmployeeId());

        test(button(panel, "edit-entry", 0)).click();
        datePickers().get(1).setValue(LocalDate.of(2026, 9, 23));
        timePickers().get(1).setValue(LocalTime.of(16, 0));
        test(button(find(Dialog.class).single(), "edit-save")).click();
        TimeEntry closed = timeEntries.findById(open.getId()).orElseThrow();
        assertEquals(Instant.parse("2026-09-23T16:00:00Z"), closed.getCheckOutAt());
        assertNull(closed.getOpenEmployeeId());
    }

    // --- Main Flow: Delete ----------------------------------------------------------------------

    @Test
    void mainFlow_delete_removesTheEntryAndAuditsTheDeletedTimesWithTheReason() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        record("2026-09-23T13:00:00Z", "2026-09-23T15:00:00Z");
        auditBefore = auditLog.findAllByOrderByIdAsc().size();
        TimeTrackingPanel panel = openPanel();
        assertEquals(2, labels(panel).size());

        test(button(panel, "delete-entry", 0)).click();

        Dialog dialog = find(Dialog.class).single();
        assertTrue(text(dialog).contains("Are you sure you want to delete this time entry? This cannot be undone."),
                text(dialog));
        reasonField().setValue("Entered twice");
        test(button(dialog, "delete-confirm")).click();

        assertTrue(timeEntries.findById(entry.getId()).isEmpty());
        assertEquals(1, timeEntries.count());
        assertFalse(dialog.isOpened());
        assertTrue(text(panel).contains("Time entry deleted."), text(panel));
        assertEquals(List.of("1:00 PM – 3:00 PM, 2h 0m"), labels(panel));
        assertTrue(text(panel).contains("Total hours today: 2h 0m"), text(panel));
        AuditLogEntry audit = onlyNewAuditEntry();
        assertEquals(AuditAction.DELETE, audit.getAction());
        assertEquals(alice, audit.getUserId());
        assertEquals(entry.getId(), audit.getEntityId());
        assertTrue(audit.getOldValues().contains("2026-09-23T08:00:00Z"), audit.getOldValues());
        assertTrue(audit.getOldValues().contains("2026-09-23T12:00:00Z"), audit.getOldValues());
        assertNull(audit.getNewValues());
        assertEquals("Entered twice", audit.getReason());
    }

    @Test
    void mainFlow_delete_cancelKeepsTheEntry() {
        record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "delete-entry", 0)).click();

        test(button(find(Dialog.class).single(), "delete-cancel")).click();

        assertEquals(1, timeEntries.count());
        assertEquals(1, labels(panel).size());
    }

    @Test
    void mainFlow_delete_anOpenEntryCanBeDeletedSoTheEmployeeCanCheckInAgain() {
        clock.set(Instant.parse("2026-09-23T09:00:00Z"));
        service.checkIn(alice);
        clock.set(NOW);
        TimeTrackingPanel panel = openPanel();

        test(button(panel, "delete-entry", 0)).click();
        test(button(find(Dialog.class).single(), "delete-confirm")).click();

        assertEquals(0, timeEntries.count());
        service.checkIn(alice); // no open entry is left behind
        assertEquals(1, timeEntries.count());
    }

    // --- AF-1 / AF-2: Validation -----------------------------------------------------------------

    @Test
    void af1_checkOutNotAfterCheckIn_showsAnErrorAndKeepsTheFormOpen() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "edit-entry", 0)).click();
        Dialog dialog = find(Dialog.class).single();

        timePickers().get(1).setValue(LocalTime.of(7, 0)); // before the check-in
        test(button(dialog, "edit-save")).click();
        assertTrue(dialog.isOpened(), "The user remains in the edit form");
        assertTrue(text(dialog).contains("Check-out time must be after check-in time."), text(dialog));
        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), timeEntries.findById(entry.getId()).orElseThrow()
                .getCheckOutAt());

        timePickers().get(1).setValue(LocalTime.of(8, 0)); // equal to the check-in is not "after" either
        test(button(dialog, "edit-save")).click();
        assertTrue(text(dialog).contains("Check-out time must be after check-in time."), text(dialog));

        timePickers().get(1).setValue(LocalTime.of(11, 0)); // corrected and resubmitted
        test(button(dialog, "edit-save")).click();
        assertFalse(dialog.isOpened());
        assertEquals(Instant.parse("2026-09-23T11:00:00Z"), timeEntries.findById(entry.getId()).orElseThrow()
                .getCheckOutAt());
    }

    @Test
    void af2_timesInTheFuture_areRefused() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "edit-entry", 0)).click();
        Dialog dialog = find(Dialog.class).single();

        timePickers().get(0).setValue(LocalTime.of(18, 0)); // check-in after "now" (17:00)
        timePickers().get(1).setValue(LocalTime.of(19, 0));
        test(button(dialog, "edit-save")).click();
        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("You cannot log time in the future."), text(dialog));

        timePickers().get(0).setValue(LocalTime.of(8, 0));
        datePickers().get(1).setValue(LocalDate.of(2026, 9, 24)); // check-out tomorrow
        timePickers().get(1).setValue(LocalTime.of(1, 0));
        test(button(dialog, "edit-save")).click();
        assertTrue(text(dialog).contains("You cannot log time in the future."), text(dialog));
        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), timeEntries.findById(entry.getId()).orElseThrow()
                .getCheckOutAt());
    }

    // --- AF-3: Timesheet Already Submitted -------------------------------------------------------

    @Test
    void af3_submittedTimesheet_disablesEditAndDeleteAndExplainsWhy() {
        record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        timesheets.save(new Timesheet(alice, YearMonth.of(2026, 9), TimesheetStatus.SUBMITTED));

        TimeTrackingPanel panel = openPanel();

        assertFalse(button(panel, "edit-entry", 0).isEnabled());
        assertFalse(button(panel, "delete-entry", 0).isEnabled());
        assertTrue(text(panel).contains("This entry cannot be edited because the timesheet has been submitted. "
                + "Wait for approval or rejection before making changes."), text(panel));
    }

    @Test
    void af3_anApprovedTimesheetIsLockedToo() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        timesheets.save(new Timesheet(alice, YearMonth.of(2026, 9), TimesheetStatus.APPROVED));

        assertEquals(TimesheetStatus.APPROVED, assertThrows(EntryLockedException.class, () -> service.correct(
                alice, entry.getId(), entry.getCheckInAt(), Instant.parse("2026-09-23T11:00:00Z"), null, UTC))
                .getStatus());
        assertThrows(EntryLockedException.class, () -> service.delete(alice, entry.getId(), null, UTC));
        assertEquals(1, timeEntries.count(), "Nothing was changed or deleted");
        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), timeEntries.findAll().getFirst().getCheckOutAt());
    }

    /** A rejected timesheet is sent back to the employee to be corrected (UC-007). */
    @Test
    void af3_aRejectedTimesheetCanBeCorrected() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        timesheets.save(new Timesheet(alice, YearMonth.of(2026, 9), TimesheetStatus.REJECTED));

        service.correct(alice, entry.getId(), entry.getCheckInAt(), Instant.parse("2026-09-23T11:00:00Z"), null,
                UTC);
        assertEquals(Instant.parse("2026-09-23T11:00:00Z"), timeEntries.findAll().getFirst().getCheckOutAt());
        service.delete(alice, entry.getId(), null, UTC);
        assertEquals(0, timeEntries.count());
    }

    @Test
    void af3_aTimesheetOfAnotherMonthDoesNotLockThisOne() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        timesheets.save(new Timesheet(alice, YearMonth.of(2026, 8), TimesheetStatus.APPROVED));

        TimeTrackingPanel panel = openPanel();

        assertTrue(button(panel, "edit-entry", 0).isEnabled());
        service.correct(alice, entry.getId(), entry.getCheckInAt(), Instant.parse("2026-09-23T11:00:00Z"), null, UTC);
    }

    @Test
    void af3_aTimesheetSubmittedWhileTheDialogIsOpenRefusesTheChange() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "edit-entry", 0)).click();
        timePickers().get(1).setValue(LocalTime.of(11, 0));

        timesheets.save(new Timesheet(alice, YearMonth.of(2026, 9), TimesheetStatus.SUBMITTED)); // meanwhile
        test(button(find(Dialog.class).single(), "edit-save")).click();

        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), timeEntries.findById(entry.getId()).orElseThrow()
                .getCheckOutAt());
        assertTrue(text(panel).contains("This entry cannot be edited because the timesheet has been submitted."),
                text(panel));
        assertFalse(button(panel, "edit-entry", 0).isEnabled(), "The row is locked now");
    }

    // --- AF-4: Database Error --------------------------------------------------------------------

    @Test
    void af4_databaseErrorWhileSaving_keepsTheFormOpenAndAllowsRetry() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "edit-entry", 0)).click();
        Dialog dialog = find(Dialog.class).single();
        timePickers().get(1).setValue(LocalTime.of(11, 0));
        int audits = auditLog.findAllByOrderByIdAsc().size();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timeEntries)
                .saveAndFlush(any(TimeEntry.class));

        test(button(dialog, "edit-save")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Unable to save changes. Please try again."), text(dialog));
        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), timeEntries.findById(entry.getId()).orElseThrow()
                .getCheckOutAt(), "No state change");
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());

        Mockito.reset(timeEntries);
        test(button(dialog, "edit-save")).click(); // retry
        assertFalse(dialog.isOpened());
        assertEquals(Instant.parse("2026-09-23T11:00:00Z"), timeEntries.findById(entry.getId()).orElseThrow()
                .getCheckOutAt());
    }

    @Test
    void af4_databaseErrorWhileDeleting_keepsTheDialogOpenAsTheRetryOption() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "delete-entry", 0)).click();
        Dialog dialog = find(Dialog.class).single();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timeEntries)
                .delete(any(TimeEntry.class));

        test(button(dialog, "delete-confirm")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Unable to save changes. Please try again."), text(dialog));
        assertTrue(timeEntries.findById(entry.getId()).isPresent(), "Still there");

        Mockito.reset(timeEntries);
        test(button(dialog, "delete-confirm")).click();
        assertTrue(timeEntries.findById(entry.getId()).isEmpty());
    }

    // --- AF-5: Other Validation and Concurrency Errors -------------------------------------------

    @Test
    void af5_incompleteInput_isExplainedAndNothingChanges() {
        record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "edit-entry", 0)).click();
        Dialog dialog = find(Dialog.class).single();

        timePickers().get(0).clear(); // no check-in time
        test(button(dialog, "edit-save")).click();
        assertTrue(text(dialog).contains("Please enter the check-in time."), text(dialog));

        timePickers().get(0).setValue(LocalTime.of(8, 0));
        timePickers().get(1).clear(); // a date without a time
        test(button(dialog, "edit-save")).click();
        assertTrue(text(dialog).contains("Please enter both the date and the time of the check-out."), text(dialog));

        datePickers().get(1).clear(); // no check-out at all, but the entry is completed
        test(button(dialog, "edit-save")).click();
        assertTrue(text(dialog).contains("Please enter the check-out date and time."), text(dialog));
        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), timeEntries.findAll().getFirst().getCheckOutAt());
    }

    @Test
    void af5_overlappingAnotherWorkPeriodIsRefused() {
        record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeEntry afternoon = record("2026-09-23T13:00:00Z", "2026-09-23T15:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "edit-entry", 1)).click();
        Dialog dialog = find(Dialog.class).single();

        timePickers().get(0).setValue(LocalTime.of(11, 0)); // into the morning period
        test(button(dialog, "edit-save")).click();
        assertTrue(text(dialog).contains("This period overlaps a work period you already recorded."), text(dialog));

        timePickers().get(0).setValue(LocalTime.of(12, 0)); // starting exactly when it ended is fine
        test(button(dialog, "edit-save")).click();
        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), timeEntries.findById(afternoon.getId()).orElseThrow()
                .getCheckInAt());
    }

    @Test
    void af5_anEntryDeletedInTheMeantimeIsReportedAndTheDayIsReloaded() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        TimeTrackingPanel panel = openPanel();
        test(button(panel, "edit-entry", 0)).click();
        timePickers().get(1).setValue(LocalTime.of(11, 0));
        jdbc.update("delete from time_entry where id = ?", entry.getId()); // another session deleted it

        test(button(find(Dialog.class).single(), "edit-save")).click();

        assertTrue(text(panel).contains("This time entry no longer exists."), text(panel));
        assertEquals(0, labels(panel).size());
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_anEmployeeCanOnlyChangeTheirOwnEntries() {
        Employee bob = employee("bob.other@example.com");
        clock.set(Instant.parse("2026-09-23T12:00:00Z"));
        TimeEntry bobsEntry = service.checkOutWithMissingCheckIn(bob.getId(), Instant.parse("2026-09-23T08:00:00Z"));

        assertThrows(EntryNotFoundException.class, () -> service.correct(alice, bobsEntry.getId(),
                bobsEntry.getCheckInAt(), Instant.parse("2026-09-23T11:00:00Z"), null, UTC));
        assertThrows(EntryNotFoundException.class, () -> service.delete(alice, bobsEntry.getId(), null, UTC));
        assertEquals(Instant.parse("2026-09-23T12:00:00Z"), timeEntries.findById(bobsEntry.getId()).orElseThrow()
                .getCheckOutAt(), "Bob's entry is untouched");
        clock.set(NOW);
        assertEquals(0, labels(openPanel()).size(), "Alice does not even see it");
    }

    @Test
    void br02_aMonthWithoutATimesheetRecordAndADraftAreBothEditable() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        service.correct(alice, entry.getId(), entry.getCheckInAt(), Instant.parse("2026-09-23T11:00:00Z"), null, UTC);

        timesheets.save(new Timesheet(alice, YearMonth.of(2026, 9), TimesheetStatus.DRAFT));
        service.correct(alice, entry.getId(), entry.getCheckInAt(), Instant.parse("2026-09-23T10:00:00Z"), null, UTC);

        assertEquals(Instant.parse("2026-09-23T10:00:00Z"), timeEntries.findById(entry.getId()).orElseThrow()
                .getCheckOutAt());
    }

    @Test
    void br03_aCompletedEntryCannotBeReopenedAndTheCheckOutMustBeAfterTheCheckIn() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");

        assertEquals(InvalidWorkPeriodException.Reason.CHECK_OUT_REQUIRED, assertThrows(
                InvalidWorkPeriodException.class,
                () -> service.correct(alice, entry.getId(), entry.getCheckInAt(), null, null, UTC)).getReason());
        assertEquals(InvalidWorkPeriodException.Reason.CHECK_OUT_BEFORE_CHECK_IN, assertThrows(
                InvalidWorkPeriodException.class,
                () -> service.correct(alice, entry.getId(), entry.getCheckInAt(), entry.getCheckInAt(), null, UTC))
                .getReason());
    }

    @Test
    void br04_noFutureTimes_noDateChange_noOverlap() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");

        assertEquals(InvalidWorkPeriodException.Reason.IN_THE_FUTURE, assertThrows(InvalidWorkPeriodException.class,
                () -> service.correct(alice, entry.getId(), entry.getCheckInAt(), NOW.plusSeconds(1), null, UTC))
                .getReason());
        assertEquals(InvalidWorkPeriodException.Reason.DATE_CHANGED, assertThrows(InvalidWorkPeriodException.class,
                () -> service.correct(alice, entry.getId(), Instant.parse("2026-09-22T08:00:00Z"),
                        Instant.parse("2026-09-22T12:00:00Z"), null, UTC)).getReason());
        service.correct(alice, entry.getId(), Instant.parse("2026-09-23T08:00:00Z"), NOW, null, UTC); // "now" is fine

        assertEquals(1, timeEntries.count());
    }

    @Test
    void br04_aWorkPeriodMayEndAfterMidnight() {
        clock.set(Instant.parse("2026-09-23T06:00:00Z"));
        TimeEntry overnight = service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-22T22:00:00Z"));
        clock.set(NOW);

        TimeEntry corrected = service.correct(alice, overnight.getId(), overnight.getCheckInAt(),
                Instant.parse("2026-09-23T07:30:00Z"), "Left later", UTC);

        assertEquals(Instant.parse("2026-09-23T07:30:00Z"), corrected.getCheckOutAt());
        assertEquals(LocalDate.of(2026, 9, 22), corrected.getCheckInAt().atZone(UTC).toLocalDate(),
                "It still belongs to the day it started");
    }

    @Test
    void br05_everyCorrectionIsAuditedAndABlankReasonIsRecordedAsNoReason() {
        TimeEntry entry = record("2026-09-23T08:00:00Z", "2026-09-23T12:00:00Z");
        auditBefore = auditLog.findAllByOrderByIdAsc().size();

        service.correct(alice, entry.getId(), entry.getCheckInAt(), Instant.parse("2026-09-23T11:00:00Z"), "   ", UTC);
        service.delete(alice, entry.getId(), "", UTC);

        List<AuditLogEntry> all = auditLog.findAllByOrderByIdAsc();
        List<AuditLogEntry> added = all.subList(auditBefore, all.size());
        assertEquals(List.of(AuditAction.UPDATE, AuditAction.DELETE), added.stream().map(AuditLogEntry::getAction)
                .toList());
        assertTrue(added.stream().allMatch(audit -> audit.getReason() == null));
        assertTrue(added.stream().allMatch(audit -> audit.getUserId() == alice));
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Records a completed work period in the past, then puts the clock back to "now". */
    private TimeEntry record(String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        TimeEntry entry = service.checkOutWithMissingCheckIn(alice, Instant.parse(checkIn));
        clock.set(NOW);
        return entry;
    }

    private TimeTrackingPanel openPanel() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        // The test UI starts on the home view, which would be reused with stale data: visit another view first.
        navigate(LoginView.class);
        navigate(HomeView.class);
        return find(TimeTrackingPanel.class).single();
    }

    private Button button(Component scope, String testId) {
        return button(scope, testId, 0);
    }

    private Button button(Component scope, String testId, int index) {
        return find(Button.class).from(scope).all().stream().filter(button -> testId.equals(button.getTestId()))
                .toList().get(index);
    }

    private List<DatePicker> datePickers() {
        return find(DatePicker.class).all();
    }

    private List<TimePicker> timePickers() {
        return find(TimePicker.class).all();
    }

    private TextField reasonField() {
        return find(TextField.class).single();
    }

    private List<String> labels(Component scope) {
        return find(Span.class).from(scope).all().stream().filter(span -> span.hasClassName("timeline-label"))
                .map(Span::getText).map(UC004CorrectHistoricalTimeEntries::normalize).toList();
    }

    private Employee employee(String email) {
        return employees.findByEmailIgnoreCase(email).orElseGet(() -> {
            Department department = departments.findAll().stream().findFirst().orElseThrow();
            return employees.save(new Employee(email, "Bob", "Other", Role.EMPLOYEE, department.getId()));
        });
    }

    private AuditLogEntry onlyNewAuditEntry() {
        List<AuditLogEntry> all = auditLog.findAllByOrderByIdAsc();
        List<AuditLogEntry> added = all.subList(auditBefore, all.size());
        assertEquals(1, added.size(), "Expected exactly one new audit entry but found " + added.size());
        return added.getFirst();
    }

    /** All text of a component tree, including the content of open dialogs. */
    private static String text(Component component) {
        StringBuilder text = new StringBuilder(component.getElement().getTextRecursively());
        component.getChildren().forEach(child -> text.append(' ').append(text(child)));
        return normalize(text.toString());
    }

    /** The time format separates the time from AM/PM with a narrow no-break space. */
    private static String normalize(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }
}
