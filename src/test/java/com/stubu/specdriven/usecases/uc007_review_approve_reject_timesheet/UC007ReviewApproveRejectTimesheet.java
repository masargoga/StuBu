package com.stubu.specdriven.usecases.uc007_review_approve_reject_timesheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.stubu.specdriven.approval.ApprovalsView;
import com.stubu.specdriven.approval.InvalidReasonException;
import com.stubu.specdriven.approval.PendingApproval;
import com.stubu.specdriven.approval.ReviewNotAllowedException;
import com.stubu.specdriven.approval.ReviewScope;
import com.stubu.specdriven.approval.ReviewStatusException;
import com.stubu.specdriven.approval.TimesheetReviewService;
import com.stubu.specdriven.approval.TimesheetReviewView;
import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditLogEntry;
import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetService;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.EntryLockedException;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.dom.Element;
import java.sql.Timestamp;
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
 * UC-007 Review and Approve/Reject Timesheet, as the manager Bob. It is 2026-10-05 09:00 UTC. Bob manages Alice,
 * whose September timesheet was submitted on 2026-10-02. Carol (administrator) is Bob's manager; Dora works in
 * Bob's department without reporting to him; Erin manages Frank in another department. The mail server is a mock.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee(email = "bob.manager@example.com", firstName = "Bob", lastName = "Manager", role = Role.MANAGER)
class UC007ReviewApproveRejectTimesheet extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-10-02T10:00:00Z");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final ZoneId UTC = ZoneOffset.UTC;

    @Autowired
    MutableClock clock;
    @Autowired
    TimeEntryService entries;
    @Autowired
    TimesheetReviewService reviews;
    @Autowired
    MonthlyTimesheetService monthly;
    @Autowired
    TimesheetService timesheetService;
    @MockitoSpyBean
    TimesheetRepository timesheets;
    @MockitoSpyBean
    AuditLogRepository auditLog;
    @Autowired
    TimeEntryRepository timeEntries;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    JavaMailSender mailSender;

    private long bob;
    private long alice;
    private long carol;
    private long mia;
    private long dora;
    private long frank;
    private long timesheetId;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(timesheets, mailSender, auditLog);
        clock.set(NOW);
        jdbc.update("delete from time_entry");
        jdbc.update("delete from timesheet");
        Department engineering = departments.findAll().stream().findFirst().orElseThrow();
        Department sales = departments.findAll().stream().filter(d -> "Sales".equals(d.getName())).findFirst()
                .orElseGet(() -> departments.save(new Department("Sales")));
        carol = person("carol.admin@example.com", "Carol", "Admin", Role.ADMIN, engineering, null);
        bob = person("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, engineering, carol);
        alice = person("alice.employee@example.com", "Alice", "Employee", Role.EMPLOYEE, engineering, bob);
        dora = person("dora.colleague@example.com", "Dora", "Colleague", Role.EMPLOYEE, engineering, carol);
        mia = person("mia.lead@example.com", "Mia", "Lead", Role.MANAGER, engineering, carol);
        long erin = person("erin.manager@example.com", "Erin", "Manager", Role.MANAGER, sales, carol);
        frank = person("frank.sales@example.com", "Frank", "Sales", Role.EMPLOYEE, sales, erin);
        record(alice, "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record(alice, "2026-09-01T13:00:00Z", "2026-09-01T17:00:00Z");
        record(alice, "2026-09-02T09:00:00Z", "2026-09-02T10:30:00Z");
        timesheetId = submitted(alice);
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(timesheets, mailSender, auditLog);
    }

    // --- Main Flow: Review ----------------------------------------------------------------------

    @Test
    void mainFlow_theManagerSeesTheSubmittedTimesheetsOfTheirEmployeesAndOpensOne() {
        submitted(frank); // belongs to another department: not in Bob's list
        ApprovalsView list = openList();

        List<Div> rows = approvalRows();
        assertEquals(1, rows.size());
        String row = text(rows.getFirst());
        assertTrue(row.contains("Alice Employee") && row.contains("September 2026") && row.contains("Oct 2, 2026"),
                row);
        assertTrue(text(list).contains("Pending approvals"), text(list));

        test(button(rows.getFirst(), "review")).click();

        TimesheetReviewView review = find(TimesheetReviewView.class).single();
        assertTrue(text(review).contains("Alice Employee: September 2026"), text(review));
        assertTrue(text(review).contains("Submitted on Oct 2, 2026. Awaiting approval."), text(review));
        assertTrue(text(review).contains("Total hours: 9h 30m"), text(review));
        assertTrue(text(review).contains("Break: 1h 0m"), text(review));
        assertEquals(30, dayRows().size(), "Every day of the month is listed");
        assertTrue(text(dayRows().get(0)).contains("Total 8h 0m, break 1h 0m"), text(dayRows().get(0)));
        assertTrue(text(dayRows().get(0)).contains("8:00 AM – 12:00 PM, 4h 0m"), text(dayRows().get(0)));
        assertTrue(button("approve").isEnabled());
        assertTrue(button("reject").isEnabled());
    }

    @Test
    void mainFlow_theReviewPageNeverOffersToEditEntries() {
        jdbc.update("update timesheet set status = 'REJECTED', rejected_at = ?, rejected_by = ?, rejection_reason = 'x'",
                Timestamp.from(NOW), bob); // a rejected timesheet is editable for the employee, not for the manager
        openReview(timesheetId);

        assertTrue(buttons("edit-entry").isEmpty());
        assertTrue(buttons("delete-entry").isEmpty());
    }

    @Test
    void mainFlow_pendingTimesheetsAreListedOldestSubmissionFirst() {
        long other = person("gina.new@example.com", "Gina", "New", Role.EMPLOYEE,
                departments.findAll().getFirst(), bob);
        record(other, "2026-09-03T08:00:00Z", "2026-09-03T09:00:00Z");
        timesheetService.getOrCreate(other, SEPTEMBER);
        jdbc.update("update timesheet set status = 'SUBMITTED', submitted_at = ? where employee_id = ?",
                Timestamp.from(Instant.parse("2026-10-01T08:00:00Z")), other);

        List<PendingApproval> pending = reviews.pending(bob, ReviewScope.DIRECT_REPORTS);

        assertEquals(List.of("Gina New", "Alice Employee"), pending.stream().map(PendingApproval::employeeName).toList());
    }

    @Test
    void mainFlow_theListIsEmptyWhenNothingWaits() {
        jdbc.update("update timesheet set status = 'APPROVED'");

        ApprovalsView list = openList();

        assertTrue(text(list).contains("No timesheets are waiting for your approval."), text(list));
        assertTrue(approvalRows().isEmpty());
    }

    // --- Main Flow: Approve ---------------------------------------------------------------------

    @Test
    void mainFlow_approving_storesTheDecisionAuditsItAndTellsTheEmployee() {
        openReview(timesheetId);
        test(button("approve")).click();
        Dialog dialog = find(Dialog.class).single();
        assertTrue(text(dialog).contains("Approve this timesheet?"), text(dialog));
        assertEquals(TimesheetStatus.SUBMITTED, status(), "Nothing happens before the confirmation");
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button(dialog, "approve-confirm")).click();

        assertFalse(dialog.isOpened());
        Timesheet sheet = timesheetInDatabase();
        assertEquals(TimesheetStatus.APPROVED, sheet.getStatus());
        assertEquals(NOW, sheet.getApprovedAt(), "The server clock decides the time");
        assertEquals(bob, sheet.getApprovedBy());
        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.APPROVE, entry.getAction());
        assertEquals(bob, entry.getUserId());
        assertEquals("Timesheet", entry.getEntityType());
        assertEquals(timesheetId, entry.getEntityId());
        assertEquals(NOW, entry.getTimestamp());
        assertTrue(entry.getOldValues().contains("\"status\":\"SUBMITTED\""), entry.getOldValues());
        assertTrue(entry.getNewValues().contains("\"status\":\"APPROVED\""), entry.getNewValues());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.verify(mailSender).send(mail.capture());
        assertEquals(List.of("alice.employee@example.com"), List.of(mail.getValue().getTo()));
        assertEquals("Timesheet approved: September 2026", mail.getValue().getSubject());
        assertTrue(mail.getValue().getText().contains("Your timesheet for September 2026 was approved by Bob Manager"),
                mail.getValue().getText());

        ApprovalsView list = find(ApprovalsView.class).single();
        assertTrue(text(list).contains("Timesheet approved."), text(list));
        assertTrue(text(list).contains("No timesheets are waiting for your approval."), "Removed from the queue");
    }

    @Test
    void mainFlow_theEmployeeSeesWhoApprovedAndCanNoLongerChangeAnything() {
        reviews.approve(bob, timesheetId);

        assertEquals("Bob Manager", monthly.load(alice, SEPTEMBER, UTC).approvedByName());
        long entryId = jdbc.queryForObject("select min(id) from time_entry", Long.class);
        assertThrows(EntryLockedException.class, () -> entries.delete(alice, entryId, null, UTC));
    }

    // --- Main Flow: Reject ----------------------------------------------------------------------

    @Test
    void mainFlow_rejecting_storesTheReasonAuditsItAndTellsTheEmployee() {
        openReview(timesheetId);
        test(button("reject")).click();
        Dialog dialog = find(Dialog.class).single();
        textArea(dialog).setValue("Discrepancy in Friday's hours");
        checkbox(dialog).setValue(true);
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button(dialog, "reject-confirm")).click();

        assertFalse(dialog.isOpened());
        Timesheet sheet = timesheetInDatabase();
        assertEquals(TimesheetStatus.REJECTED, sheet.getStatus());
        assertEquals(NOW, sheet.getRejectedAt());
        assertEquals(bob, sheet.getRejectedBy());
        assertEquals("Discrepancy in Friday's hours", sheet.getRejectionReason());
        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.REJECT, entry.getAction());
        assertEquals(bob, entry.getUserId());
        assertEquals("Discrepancy in Friday's hours", entry.getReason());
        assertTrue(entry.getNewValues().contains("\"status\":\"REJECTED\""), entry.getNewValues());

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.verify(mailSender).send(mail.capture());
        assertEquals("Timesheet rejected: September 2026", mail.getValue().getSubject());
        assertTrue(mail.getValue().getText().contains("Reason: Discrepancy in Friday's hours"),
                mail.getValue().getText());

        ApprovalsView list = find(ApprovalsView.class).single();
        assertTrue(text(list).contains("Timesheet rejected."), text(list));
        assertTrue(text(list).contains("No timesheets are waiting for your approval."), text(list));
    }

    @Test
    void mainFlow_afterARejectionTheEmployeeCanCorrectTheEntriesAndSeesTheReason() {
        reviews.reject(bob, timesheetId, "Please fix Monday");
        long entryId = jdbc.queryForObject("select min(id) from time_entry", Long.class);

        entries.correct(alice, entryId, Instant.parse("2026-09-01T08:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"),
                null, UTC);
        entries.delete(alice, entryId, "entered twice", UTC);

        assertEquals("Please fix Monday", monthly.load(alice, SEPTEMBER, UTC).rejectionReason());
        assertEquals(TimesheetStatus.REJECTED, status(), "Still rejected until the employee resubmits (UC-008)");
    }

    // --- AF-1: Not in SUBMITTED status ----------------------------------------------------------

    @Test
    void af1_onlySubmittedTimesheetsCanBeDecided() {
        for (TimesheetStatus status : List.of(TimesheetStatus.DRAFT, TimesheetStatus.APPROVED,
                TimesheetStatus.REJECTED)) {
            jdbc.update("update timesheet set status = ?", status.name());
            int audits = auditLog.findAllByOrderByIdAsc().size();

            TimesheetReviewView review = openReview(timesheetId);

            assertFalse(button("approve").isEnabled(), status + ": Approve disabled");
            assertFalse(button("reject").isEnabled(), status + ": Reject disabled");
            assertTrue(text(review).contains("not waiting for a decision"), text(review));
            assertEquals(status, assertThrows(ReviewStatusException.class, () -> reviews.approve(bob, timesheetId))
                    .getStatus());
            assertThrows(ReviewStatusException.class, () -> reviews.reject(bob, timesheetId, "why"));
            assertEquals(status, status());
            assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        }
        Mockito.verifyNoInteractions(mailSender);
    }

    @Test
    void af1_whenTwoManagersDecideAtTheSameTimeOnlyOneSucceeds() throws Exception {
        int sessions = 4;
        long audits = decisionAudits();
        ExecutorService executor = Executors.newFixedThreadPool(sessions);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < sessions; i++) {
                boolean approve = i % 2 == 0;
                results.add(executor.submit(() -> {
                    go.await();
                    try {
                        if (approve) {
                            reviews.approve(mia, timesheetId);
                        } else {
                            reviews.reject(bob, timesheetId, "no");
                        }
                        return true;
                    } catch (ReviewStatusException decided) {
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
            assertEquals(audits + 1, decisionAudits());
            Mockito.verify(mailSender, Mockito.times(1)).send(any(SimpleMailMessage.class));
        } finally {
            executor.shutdownNow();
        }
    }

    // --- AF-2: Permission -----------------------------------------------------------------------

    @Test
    void af2_aTimesheetOfAnUnrelatedDepartmentCannotBeReviewed() {
        long franksSheet = submitted(frank);

        openReview(franksSheet);

        ApprovalsView list = find(ApprovalsView.class).single();
        assertTrue(text(list).contains("You do not have permission to review this timesheet."), text(list));
        assertThrows(ReviewNotAllowedException.class, () -> reviews.details(bob, franksSheet, UTC));
        assertThrows(ReviewNotAllowedException.class, () -> reviews.approve(bob, franksSheet));
        assertThrows(ReviewNotAllowedException.class, () -> reviews.reject(bob, franksSheet, "no"));
        assertEquals(TimesheetStatus.SUBMITTED, statusOf(franksSheet));
    }

    @Test
    void af2_aTimesheetThatDoesNotExistIsAnswerTheSameWay() {
        openReview(999_999L);

        assertTrue(text(find(ApprovalsView.class).single()).contains("You do not have permission to review this "
                + "timesheet."));
    }

    @Test
    void af2_nobodyReviewsTheirOwnTimesheet() {
        long bobsSheet = submitted(bob);

        assertThrows(ReviewNotAllowedException.class, () -> reviews.approve(bob, bobsSheet));
        assertEquals(TimesheetStatus.SUBMITTED, statusOf(bobsSheet));
        assertThrows(ReviewNotAllowedException.class, () -> reviews.approve(carol, bobsSheet),
                "His own manager is an administrator, who does not approve");
        reviews.approve(mia, bobsSheet); // a manager of his department can
        assertEquals(TimesheetStatus.APPROVED, statusOf(bobsSheet));
        long miasSheet = submitted(mia);
        assertThrows(ReviewNotAllowedException.class, () -> reviews.approve(mia, miasSheet));
    }

    // --- AF-3 / AF-4: Cancel --------------------------------------------------------------------

    @Test
    void af3_cancellingTheRejectionChangesNothing() {
        openReview(timesheetId);
        test(button("reject")).click();
        Dialog dialog = find(Dialog.class).single();
        textArea(dialog).setValue("something");
        checkbox(dialog).setValue(true);
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button(dialog, "reject-cancel")).click();

        assertFalse(dialog.isOpened());
        assertEquals(TimesheetStatus.SUBMITTED, status());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        assertTrue(button("approve").isEnabled());
        Mockito.verifyNoInteractions(mailSender);
    }

    @Test
    void af4_cancellingTheApprovalChangesNothing() {
        openReview(timesheetId);
        test(button("approve")).click();
        Dialog dialog = find(Dialog.class).single();
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button(dialog, "approve-cancel")).click();

        assertFalse(dialog.isOpened());
        assertEquals(TimesheetStatus.SUBMITTED, status());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        Mockito.verifyNoInteractions(mailSender);
    }

    // --- AF-5 / AF-6: Database errors -----------------------------------------------------------

    @Test
    void af5_databaseErrorWhileApprovingKeepsTheTimesheetSubmittedAndOffersARetry() {
        openReview(timesheetId);
        test(button("approve")).click();
        Dialog dialog = find(Dialog.class).single();
        int audits = auditLog.findAllByOrderByIdAsc().size();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timesheets)
                .saveAndFlush(any(Timesheet.class));

        test(button(dialog, "approve-confirm")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Approval failed. Please try again."), text(dialog));
        assertEquals(TimesheetStatus.SUBMITTED, status());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        Mockito.verifyNoInteractions(mailSender);

        Mockito.reset(timesheets);
        test(button(dialog, "approve-confirm")).click(); // retry

        assertFalse(dialog.isOpened());
        assertEquals(TimesheetStatus.APPROVED, status());
        Mockito.verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void af6_databaseErrorWhileRejectingKeepsTheTimesheetSubmittedAndOffersARetry() {
        openReview(timesheetId);
        test(button("reject")).click();
        Dialog dialog = find(Dialog.class).single();
        textArea(dialog).setValue("Wrong hours");
        checkbox(dialog).setValue(true);
        int audits = auditLog.findAllByOrderByIdAsc().size();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timesheets)
                .saveAndFlush(any(Timesheet.class));

        test(button(dialog, "reject-confirm")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Rejection failed. Please try again."), text(dialog));
        assertEquals(TimesheetStatus.SUBMITTED, status());
        assertNull(timesheetInDatabase().getRejectionReason());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        Mockito.verifyNoInteractions(mailSender);

        Mockito.reset(timesheets);
        test(button(dialog, "reject-confirm")).click(); // retry

        assertFalse(dialog.isOpened());
        assertEquals(TimesheetStatus.REJECTED, status());
    }

    @Test
    void af5_theDecisionAndItsAuditEntryAreStoredTogether() {
        Mockito.doThrow(new DataAccessResourceFailureException("audit store is down")).when(auditLog)
                .save(any(AuditLogEntry.class));

        assertThrows(DataAccessResourceFailureException.class, () -> reviews.approve(bob, timesheetId));

        Mockito.reset(auditLog);
        assertEquals(TimesheetStatus.SUBMITTED, status(), "The status change was rolled back");
        assertNull(timesheetInDatabase().getApprovedAt());
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br02_aManagerAlsoReviewsColleaguesOfTheirDepartmentButNotOtherDepartments() {
        long doraSheet = submitted(dora);
        long franksSheet = submitted(frank);

        assertEquals(List.of("Alice Employee"), names(reviews.pending(bob, ReviewScope.DIRECT_REPORTS)));
        assertEquals(List.of("Alice Employee", "Dora Colleague"), names(reviews.pending(bob, ReviewScope.DEPARTMENT))
                .stream().sorted().toList());
        reviews.approve(bob, doraSheet); // in his department without reporting to him
        assertEquals(TimesheetStatus.APPROVED, statusOf(doraSheet));
        assertThrows(ReviewNotAllowedException.class, () -> reviews.approve(bob, franksSheet));
    }

    @Test
    void br02_anAdministratorCanLookAtEverybodyButNeverDecides() {
        long franksSheet = submitted(frank);

        assertThrows(ReviewNotAllowedException.class, () -> reviews.approve(carol, franksSheet));
        assertThrows(ReviewNotAllowedException.class, () -> reviews.reject(carol, franksSheet, "no"));
        assertEquals(TimesheetStatus.SUBMITTED, statusOf(franksSheet));
        assertEquals("Frank Sales", reviews.details(carol, franksSheet, UTC).employeeName(), "Looking is allowed");
        assertFalse(reviews.details(carol, franksSheet, UTC).canDecide());
        assertTrue(reviews.details(bob, timesheetId, UTC).canDecide());
        assertEquals(List.of(), reviews.pending(carol, ReviewScope.DEPARTMENT), "Approvals are the managers' queue");
    }

    @Test
    void br02_employeesAndInactiveManagersReviewNothing() {
        assertThrows(ReviewNotAllowedException.class, () -> reviews.approve(alice, timesheetId));
        assertEquals(List.of(), reviews.pending(alice, ReviewScope.DIRECT_REPORTS));
        jdbc.update("update employee set is_active = false where id = ?", bob);
        try {
            assertThrows(ReviewNotAllowedException.class, () -> reviews.approve(bob, timesheetId));
            assertEquals(List.of(), reviews.pending(bob, ReviewScope.DIRECT_REPORTS));
        } finally {
            jdbc.update("update employee set is_active = true where id = ?", bob);
        }
        assertEquals(TimesheetStatus.SUBMITTED, status());
    }

    @Test
    void br03_aRejectionNeedsAReason() {
        openReview(timesheetId);
        test(button("reject")).click();
        Dialog dialog = find(Dialog.class).single();
        checkbox(dialog).setValue(true);
        textArea(dialog).setValue("   ");

        test(button(dialog, "reject-confirm")).click();

        assertTrue(dialog.isOpened());
        assertTrue(textArea(dialog).isInvalid());
        assertEquals("Please enter a reason for the rejection.", textArea(dialog).getErrorMessage());
        assertEquals(TimesheetStatus.SUBMITTED, status());

        assertThrows(InvalidReasonException.class, () -> reviews.reject(bob, timesheetId, null));
        assertThrows(InvalidReasonException.class, () -> reviews.reject(bob, timesheetId, "  "));
        assertEquals(InvalidReasonException.Problem.TOO_LONG, assertThrows(InvalidReasonException.class,
                () -> reviews.reject(bob, timesheetId, "x".repeat(1001))).getProblem());
        assertEquals(TimesheetStatus.SUBMITTED, status());
        Mockito.verifyNoInteractions(mailSender);
    }

    @Test
    void br03_theRejectionMustBeConfirmedWithTheCheckbox() {
        openReview(timesheetId);
        test(button("reject")).click();
        Dialog dialog = find(Dialog.class).single();
        textArea(dialog).setValue("Wrong hours");

        test(button(dialog, "reject-confirm")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Please tick the box to confirm the rejection."), text(dialog));
        assertEquals(TimesheetStatus.SUBMITTED, status());
    }

    @Test
    void br05_anApprovedTimesheetCannotBeDecidedAgainOrChanged() {
        reviews.approve(bob, timesheetId);

        assertThrows(ReviewStatusException.class, () -> reviews.reject(bob, timesheetId, "too late"));
        assertThrows(ReviewStatusException.class, () -> reviews.approve(mia, timesheetId));
        assertEquals(TimesheetStatus.APPROVED, status());
    }

    @Test
    void br06_theSurroundingNotificationFailureNeverUndoesTheDecision() {
        Mockito.doThrow(new MailSendException("smtp server is down")).when(mailSender)
                .send(any(SimpleMailMessage.class));

        reviews.approve(bob, timesheetId);

        assertEquals(TimesheetStatus.APPROVED, status());
    }

    // --- helpers --------------------------------------------------------------------------------

    private long person(String email, String first, String last, Role role, Department department, Long managerId) {
        Employee employee = employees.findByEmailIgnoreCase(email)
                .orElseGet(() -> new Employee(email, first, last, role, department.getId()));
        employee.setManagerId(managerId);
        employee.setActive(true);
        return employees.save(employee).getId();
    }

    private void record(long employeeId, String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        entries.checkOutWithMissingCheckIn(employeeId, Instant.parse(checkIn));
        clock.set(NOW);
    }

    /** Creates the employee's September timesheet as SUBMITTED, as UC-006 would have. */
    private long submitted(long employeeId) {
        Timesheet sheet = timesheetService.getOrCreate(employeeId, SEPTEMBER);
        jdbc.update("update timesheet set status = 'SUBMITTED', submitted_at = ? where id = ?",
                Timestamp.from(SUBMITTED_AT), sheet.getId());
        return sheet.getId();
    }

    private Timesheet timesheetInDatabase() {
        return timesheets.findById(timesheetId).orElseThrow();
    }

    private TimesheetStatus status() {
        return statusOf(timesheetId);
    }

    private TimesheetStatus statusOf(long id) {
        return timesheets.findById(id).orElseThrow().getStatus();
    }

    private long decisionAudits() {
        return auditLog.findAllByOrderByIdAsc().stream()
                .filter(entry -> entry.getAction() == AuditAction.APPROVE || entry.getAction() == AuditAction.REJECT)
                .count();
    }

    private static List<String> names(List<PendingApproval> pending) {
        return pending.stream().map(PendingApproval::employeeName).toList();
    }

    private ApprovalsView openList() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login"); // every call builds a fresh page
        UI.getCurrent().navigate(ApprovalsView.class);
        return find(ApprovalsView.class).single();
    }

    private TimesheetReviewView openReview(long id) {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login");
        UI.getCurrent().navigate(TimesheetReviewView.class, id);
        return find(TimesheetReviewView.class).all().stream().findFirst().orElse(null);
    }

    private List<Div> approvalRows() {
        return find(Div.class).all().stream().filter(div -> "approval-row".equals(div.getTestId())).toList();
    }

    private List<Div> dayRows() {
        return find(Div.class).all().stream().filter(div -> div.hasClassName("day-row")).toList();
    }

    private TextArea textArea(Component scope) {
        return find(TextArea.class).from(scope).single();
    }

    private Checkbox checkbox(Component scope) {
        return find(Checkbox.class).from(scope).single();
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
