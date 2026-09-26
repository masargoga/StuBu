package com.stubu.specdriven.usecases.uc015_add_historical_time_entries;

import static com.stubu.specdriven.testsupport.ViewTexts.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditLogEntry;
import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetView;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.EntryLockedException;
import com.stubu.specdriven.timetracking.InvalidWorkPeriodException;
import com.stubu.specdriven.timetracking.TimeEntry;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.QueryParameters;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
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
 * UC-015 Add Historical Time Entries, as Alice. The test clock says it is Wednesday 2026-09-23 17:00 UTC; the month
 * view shows September 2026 in UTC. The add dialog is driven browserless; the state of the timesheet is set directly.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee
class UC015AddHistoricalTimeEntries extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-09-23T17:00:00Z");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final String ALICE = "alice.employee@example.com";

    @Autowired
    MutableClock clock;
    @Autowired
    TimeEntryService service;
    @MockitoSpyBean
    TimeEntryRepository timeEntries;
    @Autowired
    TimesheetService timesheetService;
    @Autowired
    TimesheetRepository timesheets;
    @Autowired
    EmployeeRepository employees;
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

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_fromTheGeneralButton_addsACompletedEntryAndAuditsIt() {
        MonthlyTimesheetView view = openMonth("2026-09");

        test(button("add-time-entry")).click();

        Dialog dialog = find(Dialog.class).single();
        assertTrue(dialog.isOpened());
        assertTrue(datePicker("add-date").isEmpty(), "Nothing preset when started from the general button");
        assertEquals(LocalDate.of(2026, 9, 23), datePicker("add-date").getMax(), "Nothing after today can be chosen");
        datePicker("add-date").setValue(LocalDate.of(2026, 9, 10));
        assertEquals(LocalDate.of(2026, 9, 10), datePicker("add-check-out-date").getValue(),
                "The check-out is on the same day by default");
        timePicker("add-check-in").setValue(LocalTime.of(9, 0));
        timePicker("add-check-out").setValue(LocalTime.of(17, 30));
        reason().setValue("Forgot to record this day");
        test(button("add-save")).click();

        TimeEntry stored = only();
        assertEquals(alice, stored.getEmployeeId());
        assertEquals(Instant.parse("2026-09-10T09:00:00Z"), stored.getCheckInAt());
        assertEquals(Instant.parse("2026-09-10T17:30:00Z"), stored.getCheckOutAt());
        assertFalse(stored.isActive(), "A completed entry, not an open one");
        AuditLogEntry audit = onlyNewAuditEntry();
        assertEquals(AuditAction.CREATE, audit.getAction());
        assertEquals(alice, audit.getUserId());
        assertEquals("Forgot to record this day", audit.getReason());
        assertTrue(audit.getNewValues().contains("2026-09-10T09:00:00Z"), audit.getNewValues());
        assertNull(audit.getOldValues());
        assertFalse(dialog.isOpened(), "The form closes");
        String page = text(view);
        assertTrue(page.contains("Time entry added."), page);
        assertTrue(text(dayRow(view, "2026-09-10")).contains("9:00 AM – 5:30 PM, 8h 30m"), text(dayRow(view, "2026-09-10")));
        assertTrue(page.contains("Total hours: 8h 30m"), page);
    }

    @Test
    void mainFlow_fromADayRow_presetsTheDate() {
        MonthlyTimesheetView view = openMonth("2026-09");

        test(dayButton(view, "2026-09-10")).click();

        assertEquals(LocalDate.of(2026, 9, 10), datePicker("add-date").getValue());
        assertEquals(LocalDate.of(2026, 9, 10), datePicker("add-check-out-date").getValue());
        timePicker("add-check-in").setValue(LocalTime.of(8, 0));
        timePicker("add-check-out").setValue(LocalTime.of(12, 0));
        test(button("add-save")).click();

        assertEquals(Instant.parse("2026-09-10T08:00:00Z"), only().getCheckInAt());
    }

    @Test
    void mainFlow_theDayButtonsAreOfferedUpToAndIncludingTodayOnly() {
        MonthlyTimesheetView view = openMonth("2026-09");

        assertEquals(23, dayButtons(view).size(), "September 1 to 23 (today)");
        assertEquals(1, dayButtons(dayRow(view, "2026-09-23")).size(), "Today is included");
        assertTrue(dayButtons(dayRow(view, "2026-09-24")).isEmpty(), "Tomorrow is not");
    }

    @Test
    void mainFlow_today_canBeAdded() {
        openMonth("2026-09");

        test(button("add-time-entry")).click();
        datePicker("add-date").setValue(LocalDate.of(2026, 9, 23));
        timePicker("add-check-in").setValue(LocalTime.of(8, 0));
        timePicker("add-check-out").setValue(LocalTime.of(12, 0));
        test(button("add-save")).click();

        assertEquals(Instant.parse("2026-09-23T08:00:00Z"), only().getCheckInAt());
    }

    // --- AF-1: Save and add another -------------------------------------------------------------

    @Test
    void af1_saveAndAddAnother_keepsTheFormOpenWithTheSameDate() {
        MonthlyTimesheetView view = openMonth("2026-09");
        test(dayButton(view, "2026-09-10")).click();
        timePicker("add-check-in").setValue(LocalTime.of(8, 0));
        timePicker("add-check-out").setValue(LocalTime.of(12, 0));
        reason().setValue("Morning");

        test(button("add-save-another")).click();

        Dialog dialog = find(Dialog.class).single();
        assertTrue(dialog.isOpened());
        assertEquals(LocalDate.of(2026, 9, 10), datePicker("add-date").getValue(), "The date is kept");
        assertTrue(timePicker("add-check-in").isEmpty() && timePicker("add-check-out").isEmpty(), "The times are cleared");
        assertEquals("", reason().getValue());
        assertTrue(byTestId(Div.class, "add-success").isVisible(), "The dialog confirms");
        assertEquals(1, timeEntries.count());

        timePicker("add-check-in").setValue(LocalTime.of(13, 0));
        timePicker("add-check-out").setValue(LocalTime.of(17, 0));
        test(button("add-save")).click();

        assertFalse(dialog.isOpened());
        assertEquals(List.of(Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T13:00:00Z")),
                timeEntries.findAll().stream().map(TimeEntry::getCheckInAt).sorted().toList());
        assertEquals(2, auditLog.findAllByOrderByIdAsc().size() - auditBefore);
    }

    // --- AF-2 .. AF-4: validation ---------------------------------------------------------------

    @Test
    void af2_missingFieldsAndACheckOutBeforeTheCheckInAreRefused() {
        MonthlyTimesheetView view = openMonth("2026-09");
        test(dayButton(view, "2026-09-10")).click();

        test(button("add-save")).click();
        assertEquals("Please enter the date and the times.", dialogError());
        assertEquals(0, timeEntries.count());

        timePicker("add-check-in").setValue(LocalTime.of(12, 0));
        timePicker("add-check-out").setValue(LocalTime.of(9, 0));
        test(button("add-save")).click();
        assertEquals("Check-out time must be after check-in time.", dialogError());
        assertTrue(find(Dialog.class).single().isOpened(), "The form stays open with the entered values");
        assertEquals(LocalTime.of(12, 0), timePicker("add-check-in").getValue());
        assertEquals(0, timeEntries.count());
        assertEquals(auditBefore, auditLog.findAllByOrderByIdAsc().size());
    }

    @Test
    void af3_aTimeInTheFutureIsRefused() {
        openMonth("2026-09");
        test(button("add-time-entry")).click();
        datePicker("add-date").setValue(LocalDate.of(2026, 9, 23));
        timePicker("add-check-in").setValue(LocalTime.of(16, 0));
        timePicker("add-check-out").setValue(LocalTime.of(18, 0)); // it is 17:00

        test(button("add-save")).click();

        assertEquals("You cannot log time in the future.", dialogError());
        assertEquals(0, timeEntries.count());
    }

    @Test
    void af4_anOverlapWithAnotherPeriodOrAnOpenCheckInIsRefused() {
        service.add(alice, Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T12:00:00Z"), null,
                ZoneOffset.UTC);
        clock.set(Instant.parse("2026-09-23T15:00:00Z"));
        service.checkIn(alice); // open since 15:00, it is 17:00 again below
        clock.set(NOW);
        auditBefore = auditLog.findAllByOrderByIdAsc().size();
        MonthlyTimesheetView view = openMonth("2026-09");

        test(dayButton(view, "2026-09-10")).click();
        timePicker("add-check-in").setValue(LocalTime.of(11, 0));
        timePicker("add-check-out").setValue(LocalTime.of(13, 0));
        test(button("add-save")).click();
        assertEquals("This period overlaps a work period you already recorded.", dialogError());
        find(Dialog.class).single().close();

        test(dayButton(view, "2026-09-23")).click();
        timePicker("add-check-in").setValue(LocalTime.of(14, 0));
        timePicker("add-check-out").setValue(LocalTime.of(16, 0)); // the open check-in runs from 15:00 until now
        test(button("add-save")).click();
        assertEquals("This period overlaps a work period you already recorded.", dialogError());

        timePicker("add-check-out").setValue(LocalTime.of(14, 30)); // ends before the open check-in
        test(button("add-save")).click();
        assertEquals(3, timeEntries.count());
        assertEquals(1, auditLog.findAllByOrderByIdAsc().size() - auditBefore, "Only the accepted entry is audited");
    }

    // --- AF-5: locked timesheet -----------------------------------------------------------------

    @Test
    void af5_aSubmittedMonthOffersNoAddButtonsAndSaysWhy() {
        setStatus(SEPTEMBER, "SUBMITTED");
        MonthlyTimesheetView view = openMonth("2026-09");

        assertFalse(button("add-time-entry").isEnabled());
        assertTrue(dayButtons(view).isEmpty());
        assertTrue(text(view).contains("The entries of this timesheet cannot be edited because it has been submitted."),
                text(view));
    }

    @Test
    void af5_aTimesheetSubmittedWhileTheFormIsOpenRefusesTheSave() {
        MonthlyTimesheetView view = openMonth("2026-09");
        test(dayButton(view, "2026-09-10")).click();
        timePicker("add-check-in").setValue(LocalTime.of(8, 0));
        timePicker("add-check-out").setValue(LocalTime.of(12, 0));
        setStatus(SEPTEMBER, "SUBMITTED"); // somebody submits meanwhile

        test(button("add-save")).click();

        assertFalse(find(Dialog.class).all().stream().anyMatch(Dialog::isOpened), "The form closes");
        assertTrue(text(view).contains("This entry cannot be edited because the timesheet has been submitted."),
                text(view));
        assertEquals(0, timeEntries.count());
        assertEquals(auditBefore, auditLog.findAllByOrderByIdAsc().size());
    }

    // --- AF-6, AF-7 -----------------------------------------------------------------------------

    @Test
    void af6_aDatabaseErrorKeepsTheFormOpenAndStoresNothing() {
        MonthlyTimesheetView view = openMonth("2026-09");
        test(dayButton(view, "2026-09-10")).click();
        timePicker("add-check-in").setValue(LocalTime.of(8, 0));
        timePicker("add-check-out").setValue(LocalTime.of(12, 0));
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timeEntries)
                .saveAndFlush(any(TimeEntry.class));

        test(button("add-save")).click();

        assertEquals("Unable to save changes. Please try again.", dialogError());
        assertTrue(find(Dialog.class).single().isOpened());
        Mockito.reset(timeEntries);
        assertEquals(0, timeEntries.count());
        assertEquals(auditBefore, auditLog.findAllByOrderByIdAsc().size(), "No audit entry without the entry");

        test(button("add-save")).click(); // retry
        assertEquals(1, timeEntries.count());
    }

    @Test
    void af7_cancelStoresNothing() {
        MonthlyTimesheetView view = openMonth("2026-09");
        test(dayButton(view, "2026-09-10")).click();
        timePicker("add-check-in").setValue(LocalTime.of(8, 0));
        timePicker("add-check-out").setValue(LocalTime.of(12, 0));

        test(button("add-cancel")).click();

        assertFalse(find(Dialog.class).all().stream().anyMatch(Dialog::isOpened));
        assertEquals(0, timeEntries.count());
        assertEquals(auditBefore, auditLog.findAllByOrderByIdAsc().size());
    }

    // --- Business rules -------------------------------------------------------------------------

    @Test
    void br01_theEntryBelongsToTheSignedInEmployee() {
        MonthlyTimesheetView view = openMonth("2026-09");
        test(dayButton(view, "2026-09-10")).click();
        timePicker("add-check-in").setValue(LocalTime.of(8, 0));
        timePicker("add-check-out").setValue(LocalTime.of(12, 0));
        test(button("add-save")).click();

        assertEquals(alice, only().getEmployeeId());
        assertEquals(alice, onlyNewAuditEntry().getUserId());
    }

    @Test
    void br03_aPeriodMayEndAfterMidnightButNotInTheFuture() {
        MonthlyTimesheetView view = openMonth("2026-09");
        test(dayButton(view, "2026-09-10")).click();
        timePicker("add-check-in").setValue(LocalTime.of(22, 0));
        timePicker("add-check-out").setValue(LocalTime.of(2, 0));
        datePicker("add-check-out-date").setValue(LocalDate.of(2026, 9, 11));
        test(button("add-save")).click();

        TimeEntry stored = only();
        assertEquals(Instant.parse("2026-09-10T22:00:00Z"), stored.getCheckInAt());
        assertEquals(Instant.parse("2026-09-11T02:00:00Z"), stored.getCheckOutAt());
        assertTrue(text(dayRow(view, "2026-09-10")).contains("4h 0m"), text(dayRow(view, "2026-09-10")));

        assertThrows(InvalidWorkPeriodException.class, () -> service.add(alice, Instant.parse("2026-09-22T22:00:00Z"),
                Instant.parse("2026-09-23T17:01:00Z"), null, ZoneOffset.UTC), "Ends after now");
    }

    @Test
    void br04_theSecondsAreSetToZero() {
        TimeEntry stored = service.add(alice, Instant.parse("2026-09-10T08:00:41Z"), Instant.parse("2026-09-10T12:00:59Z"),
                null, ZoneOffset.UTC);

        assertEquals(Instant.parse("2026-09-10T08:00:00Z"), stored.getCheckInAt());
        assertEquals(Instant.parse("2026-09-10T12:00:00Z"), stored.getCheckOutAt());
    }

    @Test
    void br05_aMonthWithoutATimesheetRecordAndARejectedOneAllowAddingAndStayAsTheyAre() {
        // August has no timesheet record yet: it counts as a draft.
        MonthlyTimesheetView august = openMonth("2026-08");
        test(dayButton(august, "2026-08-14")).click();
        timePicker("add-check-in").setValue(LocalTime.of(8, 0));
        timePicker("add-check-out").setValue(LocalTime.of(12, 0));
        test(button("add-save")).click();
        assertEquals(1, timeEntries.count());

        // A rejected September stays rejected (BR-08).
        setStatus(SEPTEMBER, "REJECTED");
        service.add(alice, Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T12:00:00Z"), null,
                ZoneOffset.UTC);
        assertEquals(TimesheetStatus.REJECTED, timesheetService.statusOf(alice, SEPTEMBER));

        setStatus(SEPTEMBER, "APPROVED");
        assertThrows(EntryLockedException.class, () -> service.add(alice, Instant.parse("2026-09-11T08:00:00Z"),
                Instant.parse("2026-09-11T12:00:00Z"), null, ZoneOffset.UTC));
    }

    @Test
    void br06_theReasonIsOptionalAndAuditedInTheSameTransaction() {
        service.add(alice, Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T12:00:00Z"), "  ", ZoneOffset.UTC);

        assertEquals("Added afterwards", onlyNewAuditEntry().getReason());
    }

    @Test
    void br07_thereIsNoLimitOnTheHoursOfADay() {
        service.add(alice, Instant.parse("2026-09-10T00:00:00Z"), Instant.parse("2026-09-10T23:59:00Z"), null, ZoneOffset.UTC);

        assertEquals(1, timeEntries.count());
    }

    @Test
    void br09_theTimeZoneDecidesWhichMonthTheEntryBelongsTo() {
        clock.set(Instant.parse("2026-10-05T09:00:00Z"));
        setStatus(YearMonth.of(2026, 10), "SUBMITTED");
        ZoneId berlin = ZoneId.of("Europe/Berlin");

        // 22:30 UTC on September 30 is 00:30 on October 1 in Berlin: October, which is submitted.
        assertThrows(EntryLockedException.class, () -> service.add(alice, Instant.parse("2026-09-30T22:30:00Z"),
                Instant.parse("2026-09-30T23:30:00Z"), null, berlin));
        // In UTC the same period is still September, a draft.
        service.add(alice, Instant.parse("2026-09-30T22:30:00Z"), Instant.parse("2026-09-30T23:30:00Z"), null,
                ZoneOffset.UTC);
        assertEquals(1, timeEntries.count());
    }

    @Test
    void br10_theTimeFieldsOfferAListInStepsOf15MinutesAndStillTakeAnyMinute() {
        MonthlyTimesheetView view = openMonth("2026-09");
        test(dayButton(view, "2026-09-10")).click();

        // A step below 15 minutes would hide the list that opens with the clock icon.
        assertEquals(java.time.Duration.ofMinutes(15), timePicker("add-check-in").getStep());
        assertEquals(java.time.Duration.ofMinutes(15), timePicker("add-check-out").getStep());
        timePicker("add-check-in").setValue(LocalTime.of(8, 3));
        timePicker("add-check-out").setValue(LocalTime.of(12, 7));
        test(button("add-save")).click();

        TimeEntry stored = only();
        assertEquals(Instant.parse("2026-09-10T08:03:00Z"), stored.getCheckInAt(), "The typed minute is stored");
        assertEquals(Instant.parse("2026-09-10T12:07:00Z"), stored.getCheckOutAt());
    }

    // --- helpers --------------------------------------------------------------------------------

    private MonthlyTimesheetView openMonth(String month) {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        // The test UI starts on the home view; go through another view so every call builds a fresh page.
        UI.getCurrent().navigate("login");
        UI.getCurrent().navigate(MonthlyTimesheetView.ROUTE, QueryParameters.of("month", month));
        return find(MonthlyTimesheetView.class).single();
    }

    private void setStatus(YearMonth month, String status) {
        long id = timesheetService.getOrCreate(alice, month).getId();
        jdbc.update("update timesheet set status = ?, submitted_at = ? where id = ?", status,
                Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")), id);
    }

    private TimeEntry only() {
        List<TimeEntry> all = timeEntries.findAll();
        assertEquals(1, all.size(), "Expected exactly one entry but found " + all.size());
        return all.getFirst();
    }

    private AuditLogEntry onlyNewAuditEntry() {
        List<AuditLogEntry> all = auditLog.findAllByOrderByIdAsc();
        List<AuditLogEntry> added = all.subList(auditBefore, all.size());
        assertEquals(1, added.size(), "Expected exactly one new audit entry but found " + added.size());
        return added.getFirst();
    }

    private <T extends Component> T byTestId(Class<T> type, String testId) {
        return find(type).all().stream().filter(component -> testId.equals(component.getElement().getAttribute("data-testid")))
                .findFirst().orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " " + testId));
    }

    private Button button(String testId) {
        return byTestId(Button.class, testId);
    }

    private DatePicker datePicker(String testId) {
        return byTestId(DatePicker.class, testId);
    }

    private TimePicker timePicker(String testId) {
        return byTestId(TimePicker.class, testId);
    }

    private TextField reason() {
        return byTestId(TextField.class, "add-reason");
    }

    private String dialogError() {
        return find(Div.class).all().stream().filter(div -> div.hasClassName("time-dialog-error") && div.isVisible())
                .map(Div::getText).findFirst().orElseThrow(() -> new AssertionError("No error shown in the dialog"));
    }

    private Div dayRow(Component view, String date) {
        return find(Div.class).from(view).all().stream().filter(div -> date.equals(div.getElement().getAttribute("data-date")))
                .findFirst().orElseThrow(() -> new AssertionError("No row for " + date));
    }

    private List<Button> dayButtons(Component scope) {
        return find(Button.class).from(scope).all().stream().filter(button -> "add-day-entry".equals(button.getTestId()))
                .toList();
    }

    private Button dayButton(Component view, String date) {
        return dayButtons(dayRow(view, date)).getFirst();
    }
}
