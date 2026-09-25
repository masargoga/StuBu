package com.stubu.specdriven.usecases.uc006_submit_timesheet;

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
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetView;
import com.stubu.specdriven.monthlytimesheet.SubmissionRejectedException;
import com.stubu.specdriven.monthlytimesheet.SubmitBlocker;
import com.stubu.specdriven.monthlytimesheet.TimesheetSubmissionService;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.EntryLockedException;
import com.stubu.specdriven.timetracking.TimeEntry;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.QueryParameters;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-006 Submit Timesheet for Approval. It is 2026-10-02 09:00 UTC, so September 2026 is over and can be
 * submitted while October cannot. Alice reports to Bob; the mail server is replaced by a mock.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee
class UC006SubmitTimesheet extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final String ALICE = "alice.employee@example.com";
    private static final String BOB = "bob.manager@example.com";

    @Autowired
    MutableClock clock;
    @Autowired
    TimeEntryService service;
    @Autowired
    TimesheetSubmissionService submissions;
    @Autowired
    TimesheetService timesheetService;
    @MockitoSpyBean
    TimesheetRepository timesheets;
    @MockitoSpyBean
    AuditLogRepository auditLog;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    JavaMailSender mailSender;

    private long alice;
    private Employee bob;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(timesheets, mailSender, auditLog);
        clock.set(NOW);
        jdbc.update("delete from time_entry");
        jdbc.update("delete from timesheet");
        Employee manager = employees.findByEmailIgnoreCase(BOB).orElseGet(() -> {
            Department department = departments.findAll().stream().findFirst().orElseThrow();
            return employees.save(new Employee(BOB, "Bob", "Manager", Role.MANAGER, department.getId()));
        });
        bob = manager;
        Employee employee = employees.findByEmailIgnoreCase(ALICE).orElseThrow();
        employee.setManagerId(bob.getId());
        alice = employees.save(employee).getId();
        record("2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record("2026-09-02T09:00:00Z", "2026-09-02T10:30:00Z");
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(timesheets, mailSender, auditLog);
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_asksForConfirmationThenSubmitsAndShowsTheNewStatus() {
        MonthlyTimesheetView view = openSheet("2026-09");
        assertTrue(button("submit-timesheet").isEnabled(), "A finished month can be submitted");
        assertTrue(text(view).contains("Not submitted"), text(view));

        test(button("submit-timesheet")).click();

        Dialog dialog = find(Dialog.class).single();
        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Submit this timesheet for approval? Once submitted, you cannot make "
                + "changes until it is approved or rejected."), text(dialog));
        assertEquals(TimesheetStatus.DRAFT, statusInDatabase(), "Nothing happens before the confirmation");

        test(button(dialog, "submit-confirm")).click();

        assertFalse(dialog.isOpened());
        assertEquals(TimesheetStatus.SUBMITTED, statusInDatabase());
        assertEquals(NOW, timesheetInDatabase().getSubmittedAt(), "The server clock decides the time");
        assertTrue(text(view).contains("Timesheet submitted successfully. Awaiting manager approval."), text(view));
        assertTrue(text(view).contains("Submitted on Oct 2, 2026. Awaiting approval."), text(view));
        assertTrue(text(view).contains("Submitted"), text(view));
        assertTrue(buttons("submit-timesheet").stream().noneMatch(Component::isVisible), "No more Submit button");
        assertTrue(buttons("edit-entry").isEmpty() && buttons("delete-entry").isEmpty(), "Entries are locked");
    }

    @Test
    void mainFlow_writesAnAuditEntryForTheSubmission() {
        int before = auditLog.findAllByOrderByIdAsc().size();

        submissions.submit(alice, SEPTEMBER, UTC);

        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(before + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.SUBMIT, entry.getAction());
        assertEquals(alice, entry.getUserId());
        assertEquals("Timesheet", entry.getEntityType());
        assertEquals(timesheetInDatabase().getId(), entry.getEntityId());
        assertEquals(NOW, entry.getTimestamp());
        assertTrue(entry.getOldValues().contains("\"status\":\"DRAFT\""), entry.getOldValues());
        assertTrue(entry.getNewValues().contains("\"status\":\"SUBMITTED\""), entry.getNewValues());
        assertTrue(entry.getNewValues().contains("\"period\":\"2026-09\""), entry.getNewValues());
    }

    @Test
    void mainFlow_theManagerIsToldByEmail() {
        submissions.submit(alice, SEPTEMBER, UTC);

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.verify(mailSender).send(mail.capture());
        SimpleMailMessage message = mail.getValue();
        assertEquals(List.of(BOB), List.of(message.getTo()));
        assertEquals("Timesheet awaiting approval: Alice Employee, September 2026", message.getSubject());
        assertTrue(message.getText().startsWith("Hello Bob Manager,"), message.getText());
        assertTrue(message.getText().contains("Alice Employee has submitted the timesheet for September 2026"),
                message.getText());
    }

    @Test
    void mainFlow_submittedEntriesCanNoLongerBeChanged() {
        long entryId = firstEntryId();

        submissions.submit(alice, SEPTEMBER, UTC);

        assertThrows(EntryLockedException.class, () -> service.delete(alice, entryId, null, UTC));
        assertThrows(EntryLockedException.class, () -> service.correct(alice, entryId,
                Instant.parse("2026-09-01T07:00:00Z"), Instant.parse("2026-09-01T12:00:00Z"), null, UTC));
    }

    // --- AF-1: Timesheet Already Submitted ------------------------------------------------------

    @Test
    void af1_aSubmittedTimesheetOffersNoSubmitButtonAndCannotBeSubmittedAgain() {
        submissions.submit(alice, SEPTEMBER, UTC);
        Mockito.reset(mailSender);
        int audits = auditLog.findAllByOrderByIdAsc().size();

        MonthlyTimesheetView view = openSheet("2026-09");

        assertTrue(text(view).contains("Submitted on Oct 2, 2026"), text(view));
        assertTrue(buttons("submit-timesheet").stream().noneMatch(Component::isVisible));
        SubmissionRejectedException again = assertThrows(SubmissionRejectedException.class,
                () -> submissions.submit(alice, SEPTEMBER, UTC));
        assertEquals(SubmitBlocker.NOT_DRAFT, again.getBlocker());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size(), "Not logged twice");
        Mockito.verifyNoInteractions(mailSender);
    }

    @Test
    void af1_approvedAndRejectedTimesheetsCannotBeSubmitted() {
        timesheetService.getOrCreate(alice, SEPTEMBER);
        for (TimesheetStatus status : List.of(TimesheetStatus.APPROVED, TimesheetStatus.REJECTED)) {
            jdbc.update("update timesheet set status = ?", status.name());

            SubmissionRejectedException rejected = assertThrows(SubmissionRejectedException.class,
                    () -> submissions.submit(alice, SEPTEMBER, UTC));

            assertEquals(SubmitBlocker.NOT_DRAFT, rejected.getBlocker());
            assertEquals(status, statusInDatabase());
        }
        MonthlyTimesheetView view = openSheet("2026-09");
        assertFalse(button("submit-timesheet").isEnabled(), "A rejected timesheet is resubmitted with UC-008");
        assertEquals("Edit & resubmit", button("submit-timesheet").getText());
        assertTrue(text(view).contains("not available yet"), text(view));
    }

    @Test
    void af1_whenTwoSessionsSubmitAtTheSameTimeOnlyOneSucceeds() throws Exception {
        timesheetService.getOrCreate(alice, SEPTEMBER);
        long submitAudits = submitAudits();
        int sessions = 4;
        ExecutorService executor = Executors.newFixedThreadPool(sessions);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < sessions; i++) {
                results.add(executor.submit(() -> {
                    go.await();
                    try {
                        submissions.submit(alice, SEPTEMBER, UTC);
                        return true;
                    } catch (SubmissionRejectedException alreadySubmitted) {
                        return false;
                    }
                }));
            }
            go.countDown();
            long succeeded = 0;
            for (Future<Boolean> result : results) {
                succeeded += result.get() ? 1 : 0;
            }
            assertEquals(1, succeeded);
            assertEquals(submitAudits + 1, submitAudits());
            Mockito.verify(mailSender, Mockito.times(1)).send(any(SimpleMailMessage.class));
        } finally {
            executor.shutdownNow();
        }
    }

    // --- AF-2: No Time Entries ------------------------------------------------------------------

    @Test
    void af2_anEmptyTimesheetCannotBeSubmitted() {
        MonthlyTimesheetView view = openSheet("2026-08"); // nothing recorded in August
        assertTrue(text(view).contains("No time entries recorded for August 2026."), text(view));

        test(button("submit-timesheet")).click();

        assertTrue(text(view).contains("Cannot submit an empty timesheet. Add at least one time entry."),
                text(view));
        assertTrue(find(Dialog.class).all().stream().noneMatch(Dialog::isOpened), "No confirmation for nothing");
        assertEquals(TimesheetStatus.DRAFT, statusInDatabase(YearMonth.of(2026, 8)));
        SubmissionRejectedException rejected = assertThrows(SubmissionRejectedException.class,
                () -> submissions.submit(alice, YearMonth.of(2026, 8), UTC));
        assertEquals(SubmitBlocker.EMPTY, rejected.getBlocker());
        Mockito.verifyNoInteractions(mailSender);
    }

    // --- AF-3: Employee Cancels Submission ------------------------------------------------------

    @Test
    void af3_cancellingTheConfirmationChangesNothing() {
        MonthlyTimesheetView view = openSheet("2026-09");
        int audits = auditLog.findAllByOrderByIdAsc().size();
        test(button("submit-timesheet")).click();
        Dialog dialog = find(Dialog.class).single();

        test(button(dialog, "submit-cancel")).click();

        assertFalse(dialog.isOpened());
        assertEquals(TimesheetStatus.DRAFT, statusInDatabase());
        assertNull(timesheetInDatabase().getSubmittedAt());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        assertTrue(text(view).contains("Not submitted"), text(view));
        assertTrue(button("submit-timesheet").isEnabled(), "Can still be submitted");
        Mockito.verifyNoInteractions(mailSender);
    }

    // --- AF-4: Database Error During Submission -------------------------------------------------

    @Test
    void af4_databaseErrorKeepsTheDraftAndOffersARetry() {
        openSheet("2026-09");
        int audits = auditLog.findAllByOrderByIdAsc().size();
        test(button("submit-timesheet")).click();
        Dialog dialog = find(Dialog.class).single();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timesheets)
                .saveAndFlush(any(Timesheet.class));

        test(button(dialog, "submit-confirm")).click();

        assertTrue(dialog.isOpened(), "The dialog stays open so the user can retry");
        assertTrue(text(dialog).contains("Submission failed. Please try again."), text(dialog));
        assertEquals(TimesheetStatus.DRAFT, statusInDatabase());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size(), "No audit entry without the change");
        Mockito.verifyNoInteractions(mailSender);

        Mockito.reset(timesheets);
        test(button(dialog, "submit-confirm")).click(); // retry

        assertFalse(dialog.isOpened());
        assertEquals(TimesheetStatus.SUBMITTED, statusInDatabase());
        Mockito.verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void af4_theSubmissionAndItsAuditEntryAreStoredTogether() {
        timesheetService.getOrCreate(alice, SEPTEMBER);
        Mockito.doThrow(new DataAccessResourceFailureException("audit store is down")).when(auditLog)
                .save(any(AuditLogEntry.class));

        assertThrows(DataAccessResourceFailureException.class, () -> submissions.submit(alice, SEPTEMBER, UTC));

        Mockito.reset(auditLog);
        assertEquals(TimesheetStatus.DRAFT, statusInDatabase(), "The status change was rolled back");
        assertNull(timesheetInDatabase().getSubmittedAt());
        Mockito.verifyNoInteractions(mailSender);
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_onlyADraftCanBeSubmitted() {
        submissions.submit(alice, SEPTEMBER, UTC);
        SubmissionRejectedException rejected = assertThrows(SubmissionRejectedException.class,
                () -> submissions.submit(alice, SEPTEMBER, UTC));
        assertEquals(SubmitBlocker.NOT_DRAFT, rejected.getBlocker());
    }

    @Test
    void br02_atLeastOneTimeEntryIsRequired() {
        SubmissionRejectedException rejected = assertThrows(SubmissionRejectedException.class,
                () -> submissions.submit(alice, YearMonth.of(2026, 7), UTC));
        assertEquals(SubmitBlocker.EMPTY, rejected.getBlocker());
    }

    @Test
    void br04_theSubmissionStandsEvenIfTheManagerCannotBeNotified() {
        Mockito.doThrow(new MailSendException("smtp server is down")).when(mailSender)
                .send(any(SimpleMailMessage.class));

        submissions.submit(alice, SEPTEMBER, UTC);

        assertEquals(TimesheetStatus.SUBMITTED, statusInDatabase());
    }

    @Test
    void br04_anEmployeeWithoutManagerCanStillSubmit() {
        Employee employee = employees.findById(alice).orElseThrow();
        employee.setManagerId(null);
        employees.save(employee);

        submissions.submit(alice, SEPTEMBER, UTC);

        assertEquals(TimesheetStatus.SUBMITTED, statusInDatabase());
        Mockito.verifyNoInteractions(mailSender);
    }

    // --- Month must be over and all entries completed (decisions taken for this use case) -------

    @Test
    void theCurrentMonthCannotBeSubmittedBeforeItEnds() {
        record("2026-10-01T08:00:00Z", "2026-10-01T12:00:00Z");
        MonthlyTimesheetView view = openSheet("2026-10");

        assertFalse(button("submit-timesheet").isEnabled());
        assertTrue(text(view).contains("You can submit this timesheet once the month has ended, from Nov 1, 2026."),
                text(view));
        SubmissionRejectedException rejected = assertThrows(SubmissionRejectedException.class,
                () -> submissions.submit(alice, YearMonth.of(2026, 10), UTC));
        assertEquals(SubmitBlocker.MONTH_NOT_ENDED, rejected.getBlocker());
    }

    @Test
    void theEndOfTheMonthIsDecidedInTheUsersTimeZone() {
        clock.set(Instant.parse("2026-10-01T00:30:00Z")); // October in Berlin, still September in New York

        submissions.submit(alice, SEPTEMBER, ZoneId.of("Europe/Berlin"));
        assertEquals(TimesheetStatus.SUBMITTED, statusInDatabase());

        jdbc.update("update timesheet set status = 'DRAFT', submitted_at = null");
        SubmissionRejectedException rejected = assertThrows(SubmissionRejectedException.class,
                () -> submissions.submit(alice, SEPTEMBER, ZoneId.of("America/New_York")));
        assertEquals(SubmitBlocker.MONTH_NOT_ENDED, rejected.getBlocker());
    }

    @Test
    void aStillOpenCheckInBlocksTheSubmission() {
        clock.set(Instant.parse("2026-09-30T22:00:00Z"));
        service.checkIn(alice); // forgot to check out
        clock.set(NOW);
        MonthlyTimesheetView view = openSheet("2026-09");

        test(button("submit-timesheet")).click();

        assertTrue(text(view).contains("You are still checked in during this month."), text(view));
        assertTrue(find(Dialog.class).all().stream().noneMatch(Dialog::isOpened));
        SubmissionRejectedException rejected = assertThrows(SubmissionRejectedException.class,
                () -> submissions.submit(alice, SEPTEMBER, UTC));
        assertEquals(SubmitBlocker.OPEN_ENTRY, rejected.getBlocker());
        assertEquals(TimesheetStatus.DRAFT, statusInDatabase());
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Records a completed work period in the past, then puts the clock back to "now". */
    private void record(String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        service.checkOutWithMissingCheckIn(alice, Instant.parse(checkIn));
        clock.set(NOW);
    }

    private long submitAudits() {
        return auditLog.findAllByOrderByIdAsc().stream().filter(entry -> entry.getAction() == AuditAction.SUBMIT).count();
    }

    private long firstEntryId() {
        return jdbc.queryForObject("select min(id) from time_entry where employee_id = ?", Long.class, alice);
    }

    private Timesheet timesheetInDatabase() {
        return timesheetInDatabase(SEPTEMBER);
    }

    private Timesheet timesheetInDatabase(YearMonth month) {
        return timesheets.findByEmployeeIdAndYearAndMonth(alice, month.getYear(), month.getMonthValue())
                .orElseThrow();
    }

    private TimesheetStatus statusInDatabase() {
        return statusInDatabase(SEPTEMBER);
    }

    private TimesheetStatus statusInDatabase(YearMonth month) {
        return timesheetInDatabase(month).getStatus();
    }

    private MonthlyTimesheetView openSheet(String month) {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        // The test UI starts on the home view; go through another view so every call builds a fresh page.
        UI.getCurrent().navigate("login");
        UI.getCurrent().navigate(MonthlyTimesheetView.ROUTE, QueryParameters.of("month", month));
        return find(MonthlyTimesheetView.class).single();
    }

    private Button button(String testId) {
        return buttons(testId).stream().findFirst().orElseThrow(() -> new AssertionError("No button " + testId));
    }

    private Button button(Component scope, String testId) {
        return find(Button.class).from(scope).all().stream().filter(button -> testId.equals(button.getTestId()))
                .findFirst().orElseThrow(() -> new AssertionError("No button " + testId));
    }

    private List<Button> buttons(String testId) {
        return find(Button.class).all().stream().filter(button -> testId.equals(button.getTestId())).toList();
    }

    /** The visible text of a component tree; parts that are hidden are left out. */
    private static String text(Component component) {
        return normalize(text(component.getElement()));
    }

    private static String text(Element element) {
        if (element.isTextNode()) {
            return element.getText();
        }
        if (!element.isVisible()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        element.getChildren().forEach(child -> text.append(' ').append(text(child)));
        return text.toString();
    }

    /** The time format separates the time from AM/PM with a narrow no-break space. */
    private static String normalize(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }
}
