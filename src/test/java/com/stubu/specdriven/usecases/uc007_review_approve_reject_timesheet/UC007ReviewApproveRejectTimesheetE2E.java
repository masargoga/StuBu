package com.stubu.specdriven.usecases.uc007_review_approve_reject_timesheet;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timetracking.TimeEntryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-007 Review and Approve/Reject Timesheet in a real browser: the approvals list, the review page, both
 * dialogs and the messages at the supported screen sizes. The behaviour behind it is covered by
 * {@code UC007ReviewApproveRejectTimesheet}. It is 2026-10-05 09:00 UTC (browser and server).
 */
class UC007ReviewApproveRejectTimesheetE2E extends E2ETest {

    private static final String BOB = "bob.manager@example.com";
    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

    @Autowired
    TimeEntryService service;
    @Autowired
    TimesheetService timesheetService;
    @Autowired
    JdbcTemplate jdbc;

    private long timesheetId;
    private long otherTimesheetId;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    /** Bob manages Alice, who submitted September; Frank reports to nobody Bob knows. */
    @BeforeEach
    void submitSomeTimesheets() {
        jdbc.update("delete from timesheet");
        Employee bob = employee(BOB, "Bob", "Manager", Role.MANAGER, true);
        Employee alice = employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true);
        Employee frank = employee("frank.other@example.com", "Frank", "Other", Role.EMPLOYEE, true);
        jdbc.update("update employee set manager_id = ? where id = ?", bob.getId(), alice.getId());
        jdbc.update("update employee set manager_id = null where id = ?", frank.getId());
        jdbc.update("update employee set department_id = (select min(id) from department) where id = ?",
                bob.getId());
        record(alice.getId(), "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record(alice.getId(), "2026-09-01T13:00:00Z", "2026-09-01T17:00:00Z");
        record(alice.getId(), "2026-09-02T09:00:00Z", "2026-09-02T10:30:00Z");
        record(frank.getId(), "2026-09-03T09:00:00Z", "2026-09-03T10:30:00Z");
        timesheetId = submitted(alice.getId());
        otherTimesheetId = submitted(frank.getId());
        jdbc.update("update employee set department_id = (select max(id) from department) where id = ?",
                frank.getId());
        if (jdbc.queryForObject("select count(*) from department", Integer.class) < 2) {
            jdbc.update("insert into department (name, created_at) values ('Sales', current_timestamp)");
            jdbc.update("update employee set department_id = (select max(id) from department) where id = ?",
                    frank.getId());
        }
        clock.set(NOW);
    }

    private void record(long employeeId, String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        service.checkOutWithMissingCheckIn(employeeId, Instant.parse(checkIn));
    }

    private long submitted(long employeeId) {
        long id = timesheetService.getOrCreate(employeeId, YearMonth.of(2026, 9)).getId();
        jdbc.update("update timesheet set status = 'SUBMITTED', submitted_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")), id);
        return id;
    }

    private void signInAsBobAndOpenApprovals(Viewport viewport) {
        open(viewport);
        signIn(BOB);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/approvals"));
        page.getByTestId("approvals").waitFor();
    }

    @Test
    void onlyManagersSeeTheApprovalsAndEmployeesAreKeptOut() {
        open(DESKTOP);
        signInAsAlice();
        assertThat(page.getByTestId("nav-timesheet")).isVisible();
        assertThat(page.getByTestId("nav-approvals")).hasCount(0);

        page.navigate(url("/approvals"));

        assertThat(page.getByText("Pending approvals")).hasCount(0);
        assertThat(page.getByTestId("approvals")).hasCount(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theApprovalsListFitsEveryScreenSize(Viewport viewport) {
        open(viewport);
        signIn(BOB);
        page.locator("[data-testid=check-in]").waitFor();
        if (viewport.width() < 1024) {
            page.getByTestId("drawer-toggle").click();
        }
        assertThat(page.getByTestId("nav-approvals")).isVisible();
        page.getByTestId("nav-approvals").click();
        page.getByTestId("approvals").waitFor();

        assertThat(page.getByTestId("approvals")).containsText("Alice Employee");
        assertThat(page.getByTestId("approvals")).containsText("September 2026");
        assertThat(page.getByTestId("approvals")).not().containsText("Frank Other");
        assertThat(page.getByTestId("review")).isVisible();
        assertInsideViewport("[data-testid=review]", viewport);
        assertNoHorizontalOverflow();
        screenshot("approvals-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theReviewPageShowsTheTimesheetAndTheDecisionButtons(Viewport viewport) {
        signInAsBobAndOpenApprovals(viewport);

        page.getByTestId("review").click();

        page.getByTestId("approve").waitFor();
        assertThat(page.locator("h2")).containsText("Alice Employee: September 2026");
        assertThat(page.getByTestId("status-text")).hasText("Submitted on Oct 2, 2026. Awaiting approval.");
        assertThat(page.getByTestId("month-total")).hasText("Total hours: 9h 30m");
        assertThat(page.locator(".day-row")).hasCount(30);
        assertThat(page.getByTestId("edit-entry")).hasCount(0);
        assertInsideViewport("[data-testid=approve]", viewport);
        assertInsideViewport("[data-testid=reject]", viewport);
        assertReadable(".day-label");
        assertReadable(".day-summary");
        assertReadable(".timesheet-status");
        assertNoHorizontalOverflow();
        screenshot("review-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void approvingAsksForConfirmationAndReturnsToTheList(Viewport viewport) {
        signInAsBobAndOpenApprovals(viewport);
        page.getByTestId("review").click();
        page.getByTestId("approve").click();

        assertThat(page.getByText("Approve this timesheet?")).isVisible();
        assertInsideViewport("[data-testid=approve-confirm]", viewport);
        assertInsideViewport("[data-testid=approve-cancel]", viewport);
        assertReadable(".time-dialog-text");
        screenshot("approve-dialog-" + viewport.name());

        page.getByTestId("approve-confirm").click();

        page.getByTestId("no-approvals").waitFor();
        assertThat(page.locator(".time-message")).hasText("Timesheet approved.");
        assertThat(page.getByTestId("no-approvals")).hasText("No timesheets are waiting for your approval.");
        assertReadable(".time-message");
        assertNoHorizontalOverflow();
        screenshot("approved-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void rejectingNeedsAReasonAndAConfirmation(Viewport viewport) {
        signInAsBobAndOpenApprovals(viewport);
        page.getByTestId("review").click();
        page.getByTestId("reject").click();
        page.getByTestId("reject-confirm").waitFor();
        assertInsideViewport("[data-testid=reject-confirm]", viewport);
        assertInsideViewport("[data-testid=reject-cancel]", viewport);
        screenshot("reject-dialog-" + viewport.name());

        page.getByTestId("reject-confirm").click(); // no reason yet

        assertThat(page.getByText("Please enter a reason for the rejection.")).isVisible();
        page.locator("[data-testid=reject-reason] textarea").fill("Discrepancy in Friday's hours");
        page.getByTestId("reject-confirm").click(); // reason, but the box is not ticked

        assertThat(page.locator(".time-dialog-error"))
                .hasText("Please tick the box to confirm the rejection.");
        assertReadable(".time-dialog-error");
        screenshot("reject-dialog-errors-" + viewport.name());

        page.getByTestId("reject-confirmation").locator("input").check();
        page.getByTestId("reject-confirm").click();

        page.getByTestId("no-approvals").waitFor();
        assertThat(page.locator(".time-message")).hasText("Timesheet rejected.");
        assertNoHorizontalOverflow();
        screenshot("rejected-" + viewport.name());
    }

    @Test
    void cancellingLeavesTheTimesheetWaiting() {
        signInAsBobAndOpenApprovals(DESKTOP);
        page.getByTestId("review").click();

        page.getByTestId("reject").click();
        page.getByTestId("reject-cancel").click();
        page.getByTestId("approve").click();
        page.getByTestId("approve-cancel").click();

        assertThat(page.getByTestId("approve-confirm")).hasCount(0);
        assertThat(page.getByTestId("approve")).isEnabled();
        assertThat(page.getByTestId("status-text")).containsText("Awaiting approval");
    }

    @Test
    void aTimesheetOfSomeoneElsesTeamLeadsBackToTheListWithAMessage() {
        signInAsBobAndOpenApprovals(MOBILE);

        page.navigate(url("/approvals/review/" + otherTimesheetId));

        assertThat(page.locator(".time-message-error"))
                .hasText("You do not have permission to review this timesheet.");
        assertReadable(".time-message-error");
        assertThat(page.getByTestId("approve")).hasCount(0);
        screenshot("no-permission-mobile");
    }

    @Test
    void anAlreadyDecidedTimesheetCanOnlyBeRead() {
        jdbc.update("update timesheet set status = 'APPROVED', approved_at = ?, approved_by = "
                + "(select id from employee where email = ?) where id = ?", Timestamp.from(NOW), BOB, timesheetId);
        open(DESKTOP);
        signIn(BOB);
        page.locator("[data-testid=check-in]").waitFor();

        page.navigate(url("/approvals/review/" + timesheetId));
        page.getByTestId("approve").waitFor();

        assertThat(page.getByTestId("approve")).isDisabled();
        assertThat(page.getByTestId("reject")).isDisabled();
        assertThat(page.getByTestId("review-note")).containsText("not waiting for a decision");
        assertReadable(".status-note");
        assertThat(page.getByTestId("status-text")).containsText("Approved on Oct 5, 2026 by Bob Manager");
        screenshot("review-decided");
    }

    private void assertInsideViewport(String selector, Viewport viewport) {
        double[] box = box(selector);
        assertTrue(box[0] >= 0 && box[0] + box[2] <= viewport.width() && box[1] >= 0
                && box[1] + box[3] <= viewport.height(), selector + " must be fully visible: "
                + java.util.Arrays.toString(box));
    }
}
