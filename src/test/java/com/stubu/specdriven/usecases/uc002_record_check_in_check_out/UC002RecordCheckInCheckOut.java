package com.stubu.specdriven.usecases.uc002_record_check_in_check_out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.stubu.specdriven.timetracking.AlreadyCheckedInException;
import com.stubu.specdriven.timetracking.DaySummary;
import com.stubu.specdriven.timetracking.InvalidWorkPeriodException;
import com.stubu.specdriven.timetracking.TimeEntry;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.stubu.specdriven.timetracking.TimeTrackingPanel;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.timepicker.TimePicker;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-002 Record Check-In and Check-Out. The panel is driven browserless through its buttons and dialogs;
 * the clock is controlled by the test, so every recorded timestamp is exact.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee
class UC002RecordCheckInCheckOut extends SpringBrowserlessTest {

    private static final Instant START = TestClockConfiguration.START; // 2026-09-23 08:03:14Z
    private static final String ALICE = "alice.employee@example.com";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.ENGLISH).withZone(ZoneOffset.UTC);

    @Autowired
    MutableClock clock;
    @Autowired
    TimeEntryService service;
    @MockitoSpyBean
    TimeEntryRepository timeEntries;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    JdbcTemplate jdbc;

    private long alice;
    private int auditEntriesBefore;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(timeEntries);
        clock.set(START);
        jdbc.update("delete from time_entry");
        alice = employees.findByEmailIgnoreCase(ALICE).orElseThrow().getId();
        auditEntriesBefore = auditLog.findAllByOrderByIdAsc().size();
    }

    @AfterEach
    void resetRepository() {
        Mockito.reset(timeEntries);
    }

    // --- Main Flow: Check-In --------------------------------------------------------------------

    @Test
    void mainFlow_checkIn_startsAnOpenWorkPeriod() {
        TimeTrackingPanel panel = openPanel();

        test(button("check-in")).click();

        // 2.-3. An open TimeEntry with the server time is stored.
        TimeEntry stored = timeEntries.findByOpenEmployeeId(alice).orElseThrow();
        assertEquals(START, stored.getCheckInAt());
        assertNull(stored.getCheckOutAt());
        assertTrue(stored.isActive());
        assertEquals(alice, stored.getEmployeeId());
        // 4.-6. Timeline, message and status are updated.
        assertTrue(text(panel).contains("Checked in at " + TIME.format(START) + "."), text(panel));
        assertTrue(text(panel).contains("Currently working since " + TIME.format(START)), text(panel));
        assertEquals(1, timelineRows().size());
        assertTrue(timelineRows().getFirst().hasClassName("timeline-row-open"));
        assertTrue(text(panel).contains("in progress"), text(panel));
        assertTrue(button("check-out").hasThemeName("primary"), "Check Out is the emphasized action");
        assertFalse(button("check-in").hasThemeName("primary"));
        // Postcondition: audit entry CREATE.
        AuditLogEntry audit = onlyNewAuditEntry();
        assertEquals(AuditAction.CREATE, audit.getAction());
        assertEquals(alice, audit.getUserId());
        assertEquals("TimeEntry", audit.getEntityType());
        assertEquals(stored.getId(), audit.getEntityId());
        assertTrue(audit.getNewValues().contains(START.toString()), audit.getNewValues());
    }

    // --- Main Flow: Check-Out -------------------------------------------------------------------

    @Test
    void mainFlow_checkOut_completesTheWorkPeriod() {
        TimeTrackingPanel panel = openPanel();
        test(button("check-in")).click();
        clock.advance(Duration.ofHours(4).plusMinutes(4));
        Instant checkOut = clock.instant(); // 12:07:14
        int auditBefore = auditLog.findAllByOrderByIdAsc().size();

        test(button("check-out")).click();

        // 8.-9. The stored entry is completed with the server time.
        assertTrue(timeEntries.findByOpenEmployeeId(alice).isEmpty());
        TimeEntry stored = timeEntries.findStartedBetween(alice, START.minusSeconds(1), START.plusSeconds(1)).getFirst();
        assertEquals(checkOut, stored.getCheckOutAt());
        assertNull(stored.getOpenEmployeeId());
        // 10.-14. Duration, totals, message, status and timeline.
        assertTrue(text(panel).contains("Checked out at " + TIME.format(checkOut) + ". Worked 4h 4m."), text(panel));
        assertTrue(text(panel).contains("Worked: 4h 4m"), text(panel));
        assertTrue(text(panel).contains("Break: 0h 0m"), text(panel));
        assertTrue(text(panel).contains("Not checked in"), text(panel));
        assertEquals(1, timelineRows().size());
        assertFalse(timelineRows().getFirst().hasClassName("timeline-row-open"));
        assertTrue(button("check-in").hasThemeName("primary"), "Check In is the emphasized action again");
        // Postcondition: audit entry UPDATE with old and new values.
        List<AuditLogEntry> audits = auditLog.findAllByOrderByIdAsc();
        AuditLogEntry audit = audits.subList(auditBefore, audits.size()).getFirst();
        assertEquals(1, audits.size() - auditBefore);
        assertEquals(AuditAction.UPDATE, audit.getAction());
        assertEquals(alice, audit.getUserId());
        assertTrue(audit.getOldValues().contains("\"checkOutAt\":null"), audit.getOldValues());
        assertTrue(audit.getNewValues().contains(checkOut.toString()), audit.getNewValues());
    }

    @Test
    void mainFlow_severalWorkPeriodsInADayShowWorkedAndBreakTime() {
        // 08:03-12:03, break 1h, 13:03-17:03
        service.checkIn(alice);
        clock.advance(Duration.ofHours(4));
        service.checkOut(alice);
        clock.advance(Duration.ofHours(1));
        service.checkIn(alice);
        clock.advance(Duration.ofHours(4));
        service.checkOut(alice);

        TimeTrackingPanel panel = openPanel();

        assertEquals(2, timelineRows().size());
        assertTrue(text(panel).contains("Worked: 8h 0m"), text(panel));
        assertTrue(text(panel).contains("Break: 1h 0m"), text(panel));
    }

    @Test
    void mainFlow_timelineShowsAPeriodAsBarOnA24HourScale() {
        service.checkIn(alice);
        clock.advance(Duration.ofHours(4)); // 08:03:14 - 12:03:14

        openPanel();

        Div bar = find(Div.class).all().stream().filter(d -> d.hasClassName("timeline-bar")).findFirst().orElseThrow();
        String left = bar.getStyle().get("left");
        String width = bar.getStyle().get("width");
        assertEquals("33.558%", left, "08:03:14 is 33.558% into the day");
        assertEquals("16.667%", width, "4 hours are one sixth of the day");
    }

    // --- AF-1: Check-In When Already Active ------------------------------------------------------

    @Test
    void af1_alreadyCheckedIn_cancelKeepsEverythingAsIs() {
        service.checkIn(alice);
        clock.advance(Duration.ofMinutes(10));
        int audits = auditLog.findAllByOrderByIdAsc().size();
        TimeTrackingPanel panel = openPanel();

        test(button("check-in")).click();

        ConfirmDialog dialog = find(ConfirmDialog.class).single();
        assertTrue(dialog.isOpened());
        assertEquals("You are already checked in since " + TIME.format(START)
                + ". Do you want to replace this check-in time?", dialogText(dialog));
        test(dialog).cancel();

        List<TimeEntry> stored = timeEntries.findAll();
        assertEquals(1, stored.size());
        assertEquals(START, stored.getFirst().getCheckInAt());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size(), "Cancelling records nothing");
        assertFalse(text(panel).contains("replaced"));
    }

    @Test
    void af1_alreadyCheckedIn_replaceSetsTheNewCheckInTime() {
        service.checkIn(alice);
        clock.advance(Duration.ofMinutes(10)); // 08:13:14
        int audits = auditLog.findAllByOrderByIdAsc().size();
        TimeTrackingPanel panel = openPanel();

        test(button("check-in")).click();
        test(find(ConfirmDialog.class).single()).confirm();

        List<TimeEntry> stored = timeEntries.findAll();
        assertEquals(1, stored.size(), "Still exactly one work period");
        assertEquals(clock.instant(), stored.getFirst().getCheckInAt());
        assertTrue(stored.getFirst().isActive());
        assertTrue(text(panel).contains("Check-in replaced. You are checked in since "
                + TIME.format(clock.instant()) + "."), text(panel));
        List<AuditLogEntry> all = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, all.size());
        AuditLogEntry audit = all.getLast();
        assertEquals(AuditAction.UPDATE, audit.getAction());
        assertTrue(audit.getOldValues().contains(START.toString()), audit.getOldValues());
        assertTrue(audit.getNewValues().contains(clock.instant().toString()), audit.getNewValues());
    }

    // --- AF-2: Check-Out Without an Open Check-In -----------------------------------------------

    @Test
    void af2_notCheckedIn_askingForTheMissingStartCreatesTheWorkPeriod() {
        clock.set(Instant.parse("2026-09-23T17:00:00Z"));
        TimeTrackingPanel panel = openPanel();

        test(button("check-out")).click();

        Dialog dialog = find(Dialog.class).single();
        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("No open check-in was found. When did you start working?"), text(dialog));
        assertEquals(LocalDate.of(2026, 9, 23), find(DatePicker.class).single().getValue(),
                "The date defaults to today");
        find(TimePicker.class).single().setValue(LocalTime.of(8, 0));
        test(button("missing-confirm")).click();

        assertFalse(dialog.isOpened());
        TimeEntry stored = timeEntries.findAll().getFirst();
        assertEquals(Instant.parse("2026-09-23T08:00:00Z"), stored.getCheckInAt());
        assertEquals(Instant.parse("2026-09-23T17:00:00Z"), stored.getCheckOutAt());
        assertEquals(alice, stored.getEmployeeId());
        assertNull(stored.getOpenEmployeeId());
        assertTrue(text(panel).contains("Checked out at " + TIME.format(clock.instant()) + ". Worked 9h 0m."),
                text(panel));
        assertTrue(text(panel).contains("Worked: 9h 0m"), text(panel));
        AuditLogEntry audit = onlyNewAuditEntry();
        assertEquals(AuditAction.CREATE, audit.getAction());
        assertEquals(alice, audit.getUserId());
    }

    @Test
    void af2_notCheckedIn_cancelCreatesNothing() {
        openPanel();
        test(button("check-out")).click();
        Dialog dialog = find(Dialog.class).single();

        test(button("missing-cancel")).click();

        assertFalse(dialog.isOpened());
        assertEquals(0, timeEntries.count());
        assertEquals(auditEntriesBefore, auditLog.findAllByOrderByIdAsc().size());
    }

    @Test
    void af2_notCheckedIn_invalidInputKeepsTheDialogOpenWithAMessage() {
        // 08:00-12:00 already recorded, now it is 17:00.
        clock.set(Instant.parse("2026-09-23T12:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T08:00:00Z"));
        clock.set(Instant.parse("2026-09-23T17:00:00Z"));
        openPanel();
        test(button("check-out")).click();
        Dialog dialog = find(Dialog.class).single();
        DatePicker date = find(DatePicker.class).single();
        TimePicker time = find(TimePicker.class).single();

        // No time entered.
        test(button("missing-confirm")).click();
        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Please enter the date and time you started working."), text(dialog));

        // A start that is not in the past (tomorrow).
        date.setValue(LocalDate.of(2026, 9, 24));
        time.setValue(LocalTime.of(9, 0));
        test(button("missing-confirm")).click();
        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("The start time must be in the past."), text(dialog));

        // A start that overlaps the recorded period.
        date.setValue(LocalDate.of(2026, 9, 23));
        time.setValue(LocalTime.of(10, 0));
        test(button("missing-confirm")).click();
        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("This period overlaps a work period you already recorded."), text(dialog));

        assertEquals(1, timeEntries.count(), "Nothing was created by the invalid attempts");
    }

    @Test
    void af2_checkedInMeanwhile_createsNothingAndSaysSo() {
        clock.set(Instant.parse("2026-09-23T17:00:00Z"));
        TimeTrackingPanel panel = openPanel();
        test(button("check-out")).click();
        find(TimePicker.class).single().setValue(LocalTime.of(8, 0));

        service.checkIn(alice); // another browser tab checks in while the dialog is open
        test(button("missing-confirm")).click();

        assertFalse(find(Dialog.class).all().stream().anyMatch(Dialog::isOpened));
        assertEquals(1, timeEntries.count(), "Only the open period of the other session exists");
        assertTrue(text(panel).contains("You are already checked in since " + TIME.format(clock.instant()) + "."),
                text(panel));
    }

    // --- AF-3 / AF-4: Database Errors -----------------------------------------------------------

    @Test
    void af3_databaseErrorDuringCheckIn_showsErrorAndAllowsRetry() {
        TimeTrackingPanel panel = openPanel();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timeEntries)
                .saveAndFlush(any(TimeEntry.class));

        test(button("check-in")).click();

        assertTrue(text(panel).contains("Check-in failed. Please try again."), text(panel));
        assertEquals(0, timeEntries.count(), "No TimeEntry was created");
        assertEquals(auditEntriesBefore, auditLog.findAllByOrderByIdAsc().size(), "No audit entry either");
        assertTrue(button("check-in").isEnabled(), "Check In stays available for a retry");

        Mockito.reset(timeEntries);
        test(button("check-in")).click();
        assertEquals(1, timeEntries.count());
        assertTrue(text(panel).contains("Checked in at "), text(panel));
    }

    @Test
    void af4_databaseErrorDuringCheckOut_keepsThePeriodOpenAndAllowsRetry() {
        TimeTrackingPanel panel = openPanel();
        test(button("check-in")).click();
        clock.advance(Duration.ofHours(1));
        int audits = auditLog.findAllByOrderByIdAsc().size();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timeEntries)
                .saveAndFlush(any(TimeEntry.class));

        test(button("check-out")).click();

        assertTrue(text(panel).contains("Check-out failed. Please try again."), text(panel));
        TimeEntry stored = timeEntries.findByOpenEmployeeId(alice).orElseThrow();
        assertTrue(stored.isActive(), "The period is still open");
        assertNull(stored.getCheckOutAt());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        assertTrue(button("check-out").isEnabled(), "Check Out stays available for a retry");

        Mockito.reset(timeEntries);
        test(button("check-out")).click();
        assertTrue(timeEntries.findByOpenEmployeeId(alice).isEmpty());
        assertTrue(text(panel).contains("Worked 1h 0m."), text(panel));
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_onlyOneOpenWorkPeriodPerEmployee() {
        service.checkIn(alice);
        AlreadyCheckedInException second = assertThrows(AlreadyCheckedInException.class, () -> service.checkIn(alice));
        assertEquals(START, second.getSince());
        assertEquals(1, timeEntries.count());

        // The database itself refuses a second open period, whatever the application does.
        assertThrows(DataIntegrityViolationException.class,
                () -> timeEntries.saveAndFlush(TimeEntry.open(alice, START.plusSeconds(60))));
    }

    @Test
    void br01_simultaneousCheckInsCreateExactlyOneOpenPeriod() throws Exception {
        int sessions = 8;
        ExecutorService executor = Executors.newFixedThreadPool(sessions);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < sessions; i++) {
                results.add(executor.submit(() -> {
                    go.await();
                    try {
                        service.checkIn(alice);
                        return true;
                    } catch (AlreadyCheckedInException alreadyCheckedIn) {
                        return false;
                    }
                }));
            }
            go.countDown();
            long successes = 0;
            for (Future<Boolean> result : results) {
                successes += result.get() ? 1 : 0;
            }
            assertEquals(1, successes, "Exactly one session may check in");
            assertEquals(1, timeEntries.count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void br02_timesComeFromTheServerClock() {
        clock.set(Instant.parse("2031-01-02T03:04:05Z")); // nothing to do with the real time
        TimeEntry in = service.checkIn(alice);
        assertEquals(Instant.parse("2031-01-02T03:04:05Z"), in.getCheckInAt());

        clock.advance(Duration.ofMinutes(90));
        TimeEntry out = service.checkOut(alice);
        assertEquals(Instant.parse("2031-01-02T04:34:05Z"), out.getCheckOutAt());

        // The check-in and check-out operations take no time from the caller.
        assertEquals(List.of(long.class), List.of(parameterTypes("checkIn")));
        assertEquals(List.of(long.class), List.of(parameterTypes("checkOut")));
    }

    @Test
    void br03_severalEntriesOnTheSameDayAreAllowed() {
        for (int i = 0; i < 3; i++) {
            service.checkIn(alice);
            clock.advance(Duration.ofHours(1));
            service.checkOut(alice);
            clock.advance(Duration.ofMinutes(30));
        }

        DaySummary day = service.today(alice, ZoneOffset.UTC);

        assertEquals(3, day.entries().size());
        assertEquals(Duration.ofHours(3), day.worked());
        assertEquals(Duration.ofHours(1), day.breaks(), "Two gaps of 30 minutes");
    }

    @Test
    void br04_anEmployeeOnlySeesAndChangesTheirOwnEntries() {
        Employee bob = employee("bob.other@example.com");
        service.checkIn(bob.getId());
        clock.advance(Duration.ofHours(2));
        service.checkOut(bob.getId());
        service.checkIn(bob.getId()); // Bob is working right now

        TimeTrackingPanel panel = openPanel(); // Alice's page

        assertTrue(text(panel).contains("Not checked in"), "Bob's open period is not Alice's");
        assertEquals(0, timelineRows().size());
        test(button("check-in")).click();
        assertEquals(alice, timeEntries.findByOpenEmployeeId(alice).orElseThrow().getEmployeeId());
        assertEquals(bob.getId(), timeEntries.findByOpenEmployeeId(bob.getId()).orElseThrow().getEmployeeId());
        assertEquals(2, service.today(bob.getId(), ZoneOffset.UTC).entries().size());
        assertEquals(1, service.today(alice, ZoneOffset.UTC).entries().size());
    }

    @Test
    void br05_workPeriodsCannotStartInTheFuture() {
        InvalidWorkPeriodException future = assertThrows(InvalidWorkPeriodException.class,
                () -> service.checkOutWithMissingCheckIn(alice, clock.instant().plusSeconds(60)));
        assertEquals(InvalidWorkPeriodException.Reason.START_NOT_IN_PAST, future.getReason());

        InvalidWorkPeriodException now = assertThrows(InvalidWorkPeriodException.class,
                () -> service.checkOutWithMissingCheckIn(alice, clock.instant()));
        assertEquals(InvalidWorkPeriodException.Reason.START_NOT_IN_PAST, now.getReason());

        InvalidWorkPeriodException missing = assertThrows(InvalidWorkPeriodException.class,
                () -> service.checkOutWithMissingCheckIn(alice, null));
        assertEquals(InvalidWorkPeriodException.Reason.MISSING_START, missing.getReason());
        assertEquals(0, timeEntries.count());
    }

    @Test
    void br06_aWorkPeriodMayCrossMidnight() {
        clock.set(Instant.parse("2026-09-22T22:00:00Z"));
        service.checkIn(alice);
        clock.set(Instant.parse("2026-09-23T06:00:00Z"));

        // Next morning the period that started yesterday evening is still the one to check out of.
        TimeTrackingPanel panel = openPanel();
        assertTrue(text(panel).contains("Currently working since"), text(panel));
        assertEquals(1, timelineRows().size());
        test(button("check-out")).click();

        TimeEntry stored = timeEntries.findAll().getFirst();
        assertEquals(Instant.parse("2026-09-22T22:00:00Z"), stored.getCheckInAt());
        assertEquals(Instant.parse("2026-09-23T06:00:00Z"), stored.getCheckOutAt());
        assertTrue(text(panel).contains("Worked 8h 0m."), text(panel));
    }

    @Test
    void br06_anEnteredWorkPeriodMustNotOverlapAnother() {
        clock.set(Instant.parse("2026-09-23T12:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T08:00:00Z")); // 08:00-12:00
        clock.set(Instant.parse("2026-09-23T17:00:00Z"));

        InvalidWorkPeriodException overlap = assertThrows(InvalidWorkPeriodException.class,
                () -> service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T11:00:00Z")));
        assertEquals(InvalidWorkPeriodException.Reason.OVERLAP, overlap.getReason());
        // Starting exactly when the other period ended is fine.
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T12:00:00Z"));
        assertEquals(2, timeEntries.count());
    }

    @Test
    void br07_workedTimeCountsCompletedPeriodsAndTheElapsedTimeOfTheOpenOne() {
        service.checkIn(alice);
        clock.advance(Duration.ofHours(2));
        service.checkOut(alice); // 2h completed
        clock.advance(Duration.ofHours(1));
        service.checkIn(alice);
        clock.advance(Duration.ofMinutes(45)); // 45m elapsed on the open period

        DaySummary day = service.today(alice, ZoneOffset.UTC);

        assertEquals(Duration.ofHours(2).plusMinutes(45), day.worked());
        assertEquals(Duration.ofHours(1), day.breaks());
        assertTrue(day.open().isPresent());
    }

    // --- helpers --------------------------------------------------------------------------------

    private TimeTrackingPanel openPanel() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        // The test UI starts on the home view, which would be reused (with stale data): visit another view first.
        navigate(LoginView.class);
        navigate(HomeView.class);
        return find(TimeTrackingPanel.class).single();
    }

    private Button button(String testId) {
        return find(Button.class).all().stream()
                .filter(button -> testId.equals(button.getTestId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No button " + testId));
    }

    private List<Div> timelineRows() {
        return find(Div.class).all().stream().filter(div -> div.hasClassName("timeline-row")).toList();
    }

    /** All text of a component tree, including components of an open dialog. */
    private static String text(Component component) {
        StringBuilder text = new StringBuilder(component.getElement().getTextRecursively());
        component.getChildren().forEach(child -> text.append(' ').append(text(child)));
        return text.toString();
    }

    private static String dialogText(ConfirmDialog dialog) {
        return assertInstanceOf(String.class, dialog.getElement().getProperty("message"));
    }

    private Class<?>[] parameterTypes(String method) {
        return java.util.Arrays.stream(TimeEntryService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(method)).findFirst().orElseThrow().getParameterTypes();
    }

    private Employee employee(String email) {
        return employees.findByEmailIgnoreCase(email).orElseGet(() -> {
            Department department = departments.findAll().stream().findFirst().orElseThrow();
            return employees.save(new Employee(email, "Bob", "Other", Role.EMPLOYEE, department.getId()));
        });
    }

    private AuditLogEntry onlyNewAuditEntry() {
        List<AuditLogEntry> all = auditLog.findAllByOrderByIdAsc();
        List<AuditLogEntry> added = all.subList(auditEntriesBefore, all.size());
        assertEquals(1, added.size(), "Expected exactly one new audit entry but found " + added.size());
        return added.getFirst();
    }
}
