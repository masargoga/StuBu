package com.stubu.specdriven.usecases.uc008_resubmit_rejected_timesheet;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * UC-008 Correct and Resubmit Rejected Timesheet in a real browser: how the rejection reason, the editing and the
 * resubmit dialog look at the supported screen sizes. The behaviour behind it is covered by
 * {@code UC008ResubmitRejectedTimesheet}. It is 2026-10-05 09:00 UTC (browser and server).
 */
class UC008ResubmitRejectedTimesheetE2E extends E2ETest {

    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

    @Autowired
    TimeEntryService service;
    @Autowired
    TimesheetService timesheetService;
    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    /** Bob rejected Alice's September timesheet: "The hours on Tuesday look too long, please check them." */
    @BeforeEach
    void rejectTheTimesheet() {
        jdbc.update("delete from timesheet");
        Employee bob = employee("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, true);
        Employee alice = employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true);
        jdbc.update("update employee set manager_id = ? where id = ?", bob.getId(), alice.getId());
        record(alice.getId(), "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record(alice.getId(), "2026-09-02T09:00:00Z", "2026-09-02T18:30:00Z");
        long id = timesheetService.getOrCreate(alice.getId(), YearMonth.of(2026, 9)).getId();
        jdbc.update("update timesheet set status = 'REJECTED', submitted_at = ?, rejected_at = ?, rejected_by = ?, "
                + "rejection_reason = ? where id = ?", Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")),
                Timestamp.from(Instant.parse("2026-10-03T10:00:00Z")), bob.getId(),
                "The hours on Tuesday look too long, please check them.", id);
        clock.set(NOW);
    }

    private void record(long employeeId, String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        service.checkOutWithMissingCheckIn(employeeId, Instant.parse(checkIn));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theRejectionReasonIsProminentAndTheTimesheetCanBeCorrectedAndResubmitted(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        page.navigate(url("/timesheet?month=2026-09"));
        page.locator(".day-row").first().waitFor();

        assertThat(page.getByTestId("status-text")).hasText("Rejected on Oct 3, 2026 with reason: The hours on "
                + "Tuesday look too long, please check them.");
        assertThat(page.getByTestId("timesheet-status")).hasText("Rejected");
        assertThat(page.getByTestId("submit-timesheet")).hasText("Resubmit timesheet");
        assertThat(page.getByTestId("submit-timesheet")).isEnabled();
        assertThat(page.getByTestId("edit-entry")).hasCount(2);
        assertReadable(".timesheet-status");
        assertReadable(".status-note");
        assertNoHorizontalOverflow();
        screenshot("rejected-" + viewport.name());

        page.getByTestId("submit-timesheet").click();

        assertThat(page.getByText("Resubmit this corrected timesheet for approval?")).isVisible();
        assertInsideViewport("[data-testid=submit-confirm]", viewport);
        assertInsideViewport("[data-testid=submit-cancel]", viewport);
        assertReadable(".time-dialog-text");
        screenshot("resubmit-dialog-" + viewport.name());

        page.getByTestId("submit-confirm").click();

        assertThat(page.locator(".time-message")).hasText("Timesheet resubmitted. Awaiting manager approval.");
        assertThat(page.getByTestId("status-text")).hasText("Submitted on Oct 5, 2026 (resubmitted after the "
                + "rejection on Oct 3, 2026). Awaiting approval.");
        assertThat(page.getByTestId("submit-timesheet")).isHidden();
        assertThat(page.getByTestId("edit-entry")).hasCount(0);
        assertReadable(".time-message");
        assertNoHorizontalOverflow();
        screenshot("resubmitted-" + viewport.name());
    }

    @Test
    void anEntryCanBeCorrectedFirstAndCancellingTheDialogKeepsTheTimesheetRejected() {
        open(DESKTOP);
        signInAsAlice();
        page.navigate(url("/timesheet?month=2026-09"));
        page.locator(".day-row").first().waitFor();

        page.getByTestId("edit-entry").nth(1).click();
        page.locator("vaadin-time-picker input").nth(1).fill("17:00");
        page.locator("vaadin-time-picker input").nth(1).press("Enter");
        page.getByTestId("edit-save").click();
        assertThat(page.locator(".time-message")).hasText("Time entry updated.");
        assertThat(page.getByTestId("month-total")).hasText("Total hours: 12h 0m");

        page.getByTestId("submit-timesheet").click();
        page.getByTestId("submit-cancel").click();

        assertThat(page.getByTestId("submit-confirm")).hasCount(0);
        assertThat(page.getByTestId("timesheet-status")).hasText("Rejected");
        assertThat(page.getByTestId("submit-timesheet")).isEnabled();
    }

    private void assertInsideViewport(String selector, Viewport viewport) {
        double[] box = box(selector);
        assertTrue(box[0] >= 0 && box[0] + box[2] <= viewport.width() && box[1] >= 0
                && box[1] + box[3] <= viewport.height(), selector + " must be fully visible: "
                + java.util.Arrays.toString(box));
    }
}
