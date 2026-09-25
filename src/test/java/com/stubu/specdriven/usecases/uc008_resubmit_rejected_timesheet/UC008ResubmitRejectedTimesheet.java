package com.stubu.specdriven.usecases.uc008_resubmit_rejected_timesheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.stubu.specdriven.approval.TimesheetReviewService;
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
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.QueryParameters;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
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
 * UC-008 Correct and Resubmit Rejected Timesheet, as Alice. It is 2026-10-05 09:00 UTC. Bob rejected her September
 * timesheet on 2026-10-03 ("Please fix Monday").
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee
class UC008ResubmitRejectedTimesheet extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
    private static final Instant REJECTED_AT = Instant.parse("2026-10-03T10:00:00Z");
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
    TimesheetReviewService reviews;
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
    private long bob;
    private long timesheetId;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(timesheets, mailSender, auditLog);
        clock.set(NOW);
        jdbc.update("delete from time_entry");
        jdbc.update("delete from timesheet");
        Department department = departments.findAll().stream().findFirst().orElseThrow();
        bob = employees.findByEmailIgnoreCase(BOB).orElseGet(() -> employees.save(
                new Employee(BOB, "Bob", "Manager", Role.MANAGER, department.getId()))).getId();
        Employee employee = employees.findByEmailIgnoreCase(ALICE).orElseThrow();
        employee.setManagerId(bob);
        alice = employees.save(employee).getId();
        record("2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record("2026-09-02T09:00:00Z", "2026-09-02T10:30:00Z");
        timesheetId = timesheetService.getOrCreate(alice, SEPTEMBER).getId();
        jdbc.update("update timesheet set status = 'REJECTED', submitted_at = ?, rejected_at = ?, rejected_by = ?, "
                + "rejection_reason = 'Please fix Monday' where id = ?", Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")),
                Timestamp.from(REJECTED_AT), bob, timesheetId);
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(timesheets, mailSender, auditLog);
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_theRejectedTimesheetShowsTheReasonAndOffersEditingAndResubmitting() {
        MonthlyTimesheetView view = openSheet("2026-09");

        assertTrue(text(view).contains("Rejected on Oct 3, 2026 with reason: Please fix Monday"), text(view));
        assertEquals(2, buttons("edit-entry").size(), "The entries are editable again");
        assertEquals(2, buttons("delete-entry").size());
        assertEquals("Resubmit timesheet", button("submit-timesheet").getText());
        assertTrue(button("submit-timesheet").isEnabled());
        assertTrue(text(view).contains("Correct the entries named in the reason, then resubmit the timesheet."),
                text(view));
    }

    @Test
    void mainFlow_correctedAndResubmitted_isSubmittedAgainAuditedAndTheManagerIsToldAboutIt() {
        service.correct(alice, firstEntryId(), Instant.parse("2026-09-01T08:00:00Z"),
                Instant.parse("2026-09-01T11:00:00Z"), "Friday fixed", UTC);
        MonthlyTimesheetView view = openSheet("2026-09");
        test(button("submit-timesheet")).click();
        Dialog dialog = find(Dialog.class).single();
        assertTrue(text(dialog).contains("Resubmit this corrected timesheet for approval?"), text(dialog));
        assertEquals(TimesheetStatus.REJECTED, statusInDatabase(), "Nothing happens before the confirmation");
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button(dialog, "submit-confirm")).click();

        assertFalse(dialog.isOpened());
        Timesheet sheet = timesheetInDatabase();
        assertEquals(TimesheetStatus.SUBMITTED, sheet.getStatus());
        assertEquals(NOW, sheet.getSubmittedAt(), "A new submission time");
        assertEquals(REJECTED_AT, sheet.getRejectedAt(), "The rejection stays on the record (BR-03)");
        assertEquals("Please fix Monday", sheet.getRejectionReason());
        assertEquals(bob, sheet.getRejectedBy());

        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.SUBMIT, entry.getAction());
        assertEquals(alice, entry.getUserId());
        assertEquals(timesheetId, entry.getEntityId());
        assertEquals("Resubmission after rejection", entry.getReason());
        assertTrue(entry.getOldValues().contains("\"status\":\"REJECTED\""), entry.getOldValues());
        assertTrue(entry.getNewValues().contains("\"status\":\"SUBMITTED\""), entry.getNewValues());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.verify(mailSender).send(mail.capture());
        assertEquals(List.of(BOB), List.of(mail.getValue().getTo()));
        assertEquals("Timesheet resubmitted: Alice Employee, September 2026", mail.getValue().getSubject());
        assertTrue(mail.getValue().getText().contains("Alice Employee has corrected the rejected timesheet for "
                + "September 2026"), mail.getValue().getText());

        assertTrue(text(view).contains("Timesheet resubmitted. Awaiting manager approval."), text(view));
        assertTrue(text(view).contains("Submitted on Oct 5, 2026 (resubmitted after the rejection on Oct 3, 2026). "
                + "Awaiting approval."), text(view));
        assertTrue(buttons("submit-timesheet").stream().noneMatch(Component::isVisible));
        assertTrue(buttons("edit-entry").isEmpty(), "Locked again");
        assertThrows(EntryLockedException.class, () -> service.delete(alice, firstEntryId(), null, UTC));
        assertEquals(1, reviews.pending(bob, com.stubu.specdriven.approval.ReviewScope.DIRECT_REPORTS).size(),
                "Back in the manager's queue");
    }

    @Test
    void mainFlow_aResubmittedTimesheetCanBeRejectedAgainAndKeepsTheLatestReason() {
        long resubmissions = resubmissionAudits();
        submissions.submit(alice, SEPTEMBER, UTC);

        reviews.reject(bob, timesheetId, "Still wrong on Tuesday");

        Timesheet sheet = timesheetInDatabase();
        assertEquals(TimesheetStatus.REJECTED, sheet.getStatus());
        assertEquals("Still wrong on Tuesday", sheet.getRejectionReason());
        submissions.submit(alice, SEPTEMBER, UTC); // and can be resubmitted again
        assertEquals(TimesheetStatus.SUBMITTED, statusInDatabase());
        assertEquals(resubmissions + 2, resubmissionAudits());
    }

    // --- AF-1: Timesheet Not Rejected -----------------------------------------------------------

    @Test
    void af1_theResubmitButtonOnlyExistsForRejectedTimesheets() {
        jdbc.update("update timesheet set status = 'DRAFT'");
        openSheet("2026-09");
        assertEquals("Submit timesheet", button("submit-timesheet").getText(), "A draft is submitted, not resubmitted");

        for (TimesheetStatus status : List.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED)) {
            jdbc.update("update timesheet set status = ?", status.name());
            openSheet("2026-09");
            assertTrue(buttons("submit-timesheet").stream().noneMatch(Component::isVisible), status + ": no button");
            SubmissionRejectedException rejected = assertThrows(SubmissionRejectedException.class,
                    () -> submissions.submit(alice, SEPTEMBER, UTC));
            assertEquals(SubmitBlocker.NOT_DRAFT, rejected.getBlocker());
        }
    }

    // --- AF-2: No Time Entries After Correction -------------------------------------------------

    @Test
    void af2_aTimesheetWhoseEntriesWereAllDeletedCannotBeResubmitted() {
        jdbc.update("delete from time_entry");
        MonthlyTimesheetView view = openSheet("2026-09");

        test(button("submit-timesheet")).click();

        assertTrue(text(view).contains("Cannot resubmit an empty timesheet. Add at least one time entry."), text(view));
        assertTrue(find(Dialog.class).all().stream().noneMatch(Dialog::isOpened));
        assertEquals(TimesheetStatus.REJECTED, statusInDatabase());
        assertEquals(SubmitBlocker.EMPTY, assertThrows(SubmissionRejectedException.class,
                () -> submissions.submit(alice, SEPTEMBER, UTC)).getBlocker());
        Mockito.verifyNoInteractions(mailSender);
    }

    @Test
    void af2_aStillOpenCheckInBlocksTheResubmission() {
        clock.set(Instant.parse("2026-09-30T22:00:00Z"));
        service.checkIn(alice);
        clock.set(NOW);
        MonthlyTimesheetView view = openSheet("2026-09");

        test(button("submit-timesheet")).click();

        assertTrue(text(view).contains("You are still checked in during this month."), text(view));
        assertEquals(SubmitBlocker.OPEN_ENTRY, assertThrows(SubmissionRejectedException.class,
                () -> submissions.submit(alice, SEPTEMBER, UTC)).getBlocker());
    }

    // --- AF-3: Cancel ---------------------------------------------------------------------------

    @Test
    void af3_cancellingKeepsTheTimesheetRejectedAndEditable() {
        openSheet("2026-09");
        int audits = auditLog.findAllByOrderByIdAsc().size();
        test(button("submit-timesheet")).click();
        Dialog dialog = find(Dialog.class).single();

        test(button(dialog, "submit-cancel")).click();

        assertFalse(dialog.isOpened());
        assertEquals(TimesheetStatus.REJECTED, statusInDatabase());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        assertEquals(2, buttons("edit-entry").size(), "Still editable");
        Mockito.verifyNoInteractions(mailSender);
    }

    // --- AF-4: Database Error -------------------------------------------------------------------

    @Test
    void af4_databaseErrorKeepsTheTimesheetRejectedAndOffersARetry() {
        openSheet("2026-09");
        int audits = auditLog.findAllByOrderByIdAsc().size();
        test(button("submit-timesheet")).click();
        Dialog dialog = find(Dialog.class).single();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timesheets)
                .saveAndFlush(any(Timesheet.class));

        test(button(dialog, "submit-confirm")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Resubmission failed. Please try again."), text(dialog));
        assertEquals(TimesheetStatus.REJECTED, statusInDatabase());
        assertEquals(REJECTED_AT, timesheetInDatabase().getRejectedAt());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        Mockito.verifyNoInteractions(mailSender);

        Mockito.reset(timesheets);
        test(button(dialog, "submit-confirm")).click(); // retry

        assertFalse(dialog.isOpened());
        assertEquals(TimesheetStatus.SUBMITTED, statusInDatabase());
        Mockito.verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br05_theEmployeeCanCorrectTheEntriesSeveralTimesBeforeResubmitting() {
        long entryId = firstEntryId();
        service.correct(alice, entryId, Instant.parse("2026-09-01T08:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
                null, UTC);
        service.correct(alice, entryId, Instant.parse("2026-09-01T08:30:00Z"), Instant.parse("2026-09-01T11:30:00Z"),
                "again", UTC);
        service.delete(alice, entryId, "entered twice", UTC);

        assertEquals(TimesheetStatus.REJECTED, statusInDatabase(), "Corrections do not change the status");
        submissions.submit(alice, SEPTEMBER, UTC);
        assertEquals(TimesheetStatus.SUBMITTED, statusInDatabase());
    }

    @Test
    void br04_theResubmissionStandsEvenIfTheManagerCannotBeNotified() {
        Mockito.doThrow(new MailSendException("smtp server is down")).when(mailSender)
                .send(any(SimpleMailMessage.class));

        submissions.submit(alice, SEPTEMBER, UTC);

        assertEquals(TimesheetStatus.SUBMITTED, statusInDatabase());
    }

    // --- helpers --------------------------------------------------------------------------------

    private void record(String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        service.checkOutWithMissingCheckIn(alice, Instant.parse(checkIn));
        clock.set(NOW);
    }

    private long resubmissionAudits() {
        return auditLog.findAllByOrderByIdAsc().stream().filter(entry -> entry.getAction() == AuditAction.SUBMIT
                && "Resubmission after rejection".equals(entry.getReason())).count();
    }

    private long firstEntryId() {
        return jdbc.queryForObject("select min(id) from time_entry where employee_id = ?", Long.class, alice);
    }

    private Timesheet timesheetInDatabase() {
        return timesheets.findById(timesheetId).orElseThrow();
    }

    private TimesheetStatus statusInDatabase() {
        return timesheetInDatabase().getStatus();
    }

    private MonthlyTimesheetView openSheet(String month) {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login"); // every call builds a fresh page
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
