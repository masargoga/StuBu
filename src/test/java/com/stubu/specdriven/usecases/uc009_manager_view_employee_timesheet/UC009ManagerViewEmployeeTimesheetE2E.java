package com.stubu.specdriven.usecases.uc009_manager_view_employee_timesheet;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.monthlytimesheet.TimesheetSubmissionService;
import com.stubu.specdriven.testsupport.E2ETest;
import com.stubu.specdriven.timetracking.TimeEntryService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-009 Manager Views Employee Timesheet and Timeline in a real browser: the employee list, the read-only month
 * view with its history and the timeline of a single day at the supported screen sizes. The behaviour behind it is
 * covered by {@code UC009ManagerViewEmployeeTimesheet}. It is 2026-10-05 09:00 UTC (browser and server).
 */
class UC009ManagerViewEmployeeTimesheetE2E extends E2ETest {

    private static final String BOB = "bob.manager@example.com";
    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

    @Autowired
    TimeEntryService service;
    @Autowired
    TimesheetSubmissionService submissions;
    @Autowired
    JdbcTemplate jdbc;

    private long alice;
    private long frank;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    /** Bob manages Alice, who submitted September; Frank is nobody Bob may see. */
    @BeforeEach
    void submitATimesheet() {
        jdbc.update("delete from timesheet");
        Employee bob = employee(BOB, "Bob", "Manager", Role.MANAGER, true);
        alice = employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true).getId();
        frank = employee("frank.other@example.com", "Frank", "Other", Role.EMPLOYEE, true).getId();
        jdbc.update("update employee set manager_id = ? where id = ?", bob.getId(), alice);
        jdbc.update("update employee set manager_id = null where id = ?", frank);
        if (jdbc.queryForObject("select count(*) from department", Integer.class) < 2) {
            jdbc.update("insert into department (name, created_at) values ('Sales', current_timestamp)");
        }
        jdbc.update("update employee set department_id = (select min(id) from department) where id in (?, ?)",
                bob.getId(), alice);
        jdbc.update("update employee set department_id = (select max(id) from department) where id = ?", frank);
        record(alice, "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record(alice, "2026-09-01T13:00:00Z", "2026-09-01T17:00:00Z");
        record(alice, "2026-09-02T09:00:00Z", "2026-09-02T10:30:00Z");
        clock.set(NOW);
        submissions.submit(alice, YearMonth.of(2026, 9), ZoneOffset.UTC);
    }

    private void record(long employeeId, String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        service.checkOutWithMissingCheckIn(employeeId, Instant.parse(checkIn));
    }

    private void signInAsBobAndOpenEmployees(Viewport viewport) {
        open(viewport);
        signIn(BOB);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/employees"));
        page.getByTestId("employees").waitFor();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theEmployeeListFitsEveryScreenSizeAndCanBeSearched(Viewport viewport) {
        open(viewport);
        signIn(BOB);
        page.locator("[data-testid=check-in]").waitFor();
        if (viewport.width() < 1024) {
            page.getByTestId("drawer-toggle").click();
        }
        assertThat(page.getByTestId("nav-employees")).isVisible();
        page.getByTestId("nav-employees").click();
        page.getByTestId("employees").waitFor();

        assertThat(page.getByTestId("employees")).containsText("Alice Employee");
        assertThat(page.getByTestId("employees")).not().containsText("Frank Other");
        assertInsideViewport("[data-testid=view-timesheet]", viewport);
        assertNoHorizontalOverflow();
        screenshot("employees-" + viewport.name());

        page.locator("[data-testid=employee-search] input").fill("nobody");

        assertThat(page.getByTestId("no-employees")).hasText("No employees match your search.");
        assertReadable(".time-empty");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theEmployeeTimesheetIsReadOnlyAndShowsTheHistory(Viewport viewport) {
        signInAsBobAndOpenEmployees(viewport);
        page.getByTestId("view-timesheet").click();
        page.getByTestId("no-timesheet").waitFor(); // the current month, October, has none

        assertThat(page.getByTestId("no-timesheet")).hasText("No timesheet found for Alice Employee in October 2026.");
        assertReadable(".time-empty");
        page.getByTestId("previous-month").click();
        page.waitForURL(java.util.regex.Pattern.compile(".*month=2026-09"));
        page.locator(".day-row").first().waitFor();

        assertThat(page.getByTestId("timesheet-status")).hasText("Submitted");
        assertThat(page.getByTestId("month-total")).hasText("Total hours: 9h 30m");
        assertThat(page.locator(".day-row")).hasCount(30);
        assertThat(page.getByTestId("edit-entry")).hasCount(0);
        assertThat(page.getByTestId("history")).containsText("Alice Employee submitted the timesheet");
        assertThat(page.getByTestId("decide")).isVisible();
        assertReadable(".day-label");
        assertReadable(".history-line");
        assertReadable(".timesheet-status");
        assertNoHorizontalOverflow();
        screenshot("employee-timesheet-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void aDayCanBeOpenedToSeeItsTimeline(Viewport viewport) {
        open(viewport);
        signIn(BOB);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/employees/timesheet/" + alice + "?month=2026-09"));
        page.locator(".day-row").first().waitFor();

        page.getByTestId("day-details").first().click();

        assertThat(page.getByTestId("day-total")).hasText("Total 8h 0m, break 1h 0m");
        assertThat(page.locator(".timeline-row")).hasCount(2);
        assertThat(page.getByTestId("edit-entry")).hasCount(0);
        assertInsideViewport("[data-testid=day-close]", viewport);
        assertReadable(".timeline-label");
        screenshot("day-timeline-" + viewport.name());

        page.getByTestId("day-close").click();
        assertThat(page.getByTestId("day-close")).hasCount(0);
    }

    @Test
    void anEmployeeOutsideTheManagersScopeLeadsBackToTheListWithAMessage() {
        signInAsBobAndOpenEmployees(MOBILE);

        page.navigate(url("/employees/timesheet/" + frank));

        assertThat(page.locator(".time-message-error"))
                .hasText("You do not have permission to view this employee's timesheet.");
        assertReadable(".time-message-error");
        assertThat(page.getByTestId("employees")).isVisible();
        screenshot("no-permission-mobile");
    }

    @Test
    void theDecisionButtonLeadsToTheReviewPage() {
        open(DESKTOP);
        signIn(BOB);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/employees/timesheet/" + alice + "?month=2026-09"));
        page.getByTestId("decide").click();

        assertThat(page.getByTestId("approve")).isVisible();
    }

    private void assertInsideViewport(String selector, Viewport viewport) {
        double[] box = box(selector);
        assertTrue(box[0] >= 0 && box[0] + box[2] <= viewport.width() && box[1] >= 0
                && box[1] + box[3] <= viewport.height(), selector + " must be fully visible: "
                + java.util.Arrays.toString(box));
    }
}
