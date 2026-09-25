package com.stubu.specdriven.usecases.uc014_admin_view_all_employee_data;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.options.AriaRole;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.testsupport.E2ETest;
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
 * UC-014 Administrator View All Employee and Timesheet Data in a real browser: the employee list with search,
 * filters and sorting, and an employee's details with all their timesheets at the supported screen sizes. The
 * behaviour behind it is covered by {@code UC014AdminViewAllEmployeeData}.
 */
class UC014AdminViewAllEmployeeDataE2E extends E2ETest {

    private static final String CAROL = "carol.admin@example.com";

    @Autowired
    TimesheetService timesheetService;
    @Autowired
    JdbcTemplate jdbc;

    private long alice;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    @BeforeEach
    void setUpPeople() {
        jdbc.update("delete from timesheet");
        Employee carol = employee(CAROL, "Carol", "Admin", Role.ADMIN, true);
        Employee bob = employee("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, true);
        alice = employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true).getId();
        Employee dave = employee("dave.inactive@example.com", "Dave", "Inactive", Role.EMPLOYEE, false);
        if (jdbc.queryForObject("select count(*) from department", Integer.class) < 2) {
            jdbc.update("insert into department (name, created_at) values ('Sales', current_timestamp)");
        }
        jdbc.update("update employee set manager_id = ? where id = ?", bob.getId(), alice);
        jdbc.update("update employee set manager_id = ? where id = ?", carol.getId(), bob.getId());
        jdbc.update("update employee set department_id = (select max(id) from department) where id = ?", dave.getId());
        for (Object[] month : new Object[][] { { 7, "APPROVED" }, { 8, "REJECTED" }, { 9, "SUBMITTED" } }) {
            long id = timesheetService.getOrCreate(alice, YearMonth.of(2026, (Integer) month[0])).getId();
            jdbc.update("update timesheet set status = ?, submitted_at = ?, rejection_reason = ? where id = ?",
                    month[1], java.sql.Timestamp.from(Instant.parse("2026-09-01T10:00:00Z")),
                    "REJECTED".equals(month[1]) ? "Please fix Monday" : null, id);
        }
    }

    private void signInAndOpenList(Viewport viewport) {
        open(viewport);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/admin/employees"));
        page.getByTestId("employees").waitFor();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theListCanBeSearchedFilteredAndSorted(Viewport viewport) {
        signInAndOpenList(viewport);
        assertThat(page.getByTestId("employee-count")).containsText("employees");
        assertThat(page.getByTestId("filter-status")).isVisible();
        assertThat(page.getByTestId("sort-by")).isVisible();
        assertReadable(".audit-summary");
        assertNoHorizontalOverflow();
        screenshot("list-" + viewport.name());

        page.locator("[data-testid=employee-search] input").fill("sales");
        assertThat(page.getByTestId("manage-row").filter(new com.microsoft.playwright.Locator.FilterOptions()
                .setHasText("Dave Inactive"))).hasCount(1);
        assertThat(page.getByTestId("manage-row").filter(new com.microsoft.playwright.Locator.FilterOptions()
                .setHasText("Alice Employee"))).hasCount(0);

        page.locator("[data-testid=employee-search] input").fill("nobody with this name");
        assertThat(page.getByTestId("no-employees")).hasText("No employees match your filters.");
        assertReadable(".time-empty");
        screenshot("no-match-" + viewport.name());
        page.locator("[data-testid=employee-search] input").fill("");

        String firstAscending = page.getByTestId("manage-row").first().innerText();
        page.getByTestId("sort-direction").click();
        assertThat(page.getByTestId("sort-direction")).containsText("Descending");
        page.getByTestId("employee-count").waitFor();
        org.junit.jupiter.api.Assertions.assertNotEquals(firstAscending, page.getByTestId("manage-row").first().innerText(),
                "The order is reversed");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theDetailsShowTheRecordAndAllTimesheets(Viewport viewport) {
        signInAndOpenList(viewport);
        rowOf("Alice Employee").getByTestId("employee-details").click();
        page.getByTestId("employee-timesheets").waitFor();

        assertThat(page.getByTestId("employee-info")).containsText("alice.employee@example.com");
        assertThat(page.getByTestId("employee-info")).containsText("Bob Manager");
        assertThat(page.getByTestId("timesheet-row")).hasCount(3);
        assertThat(page.getByTestId("timesheet-row").nth(1)).containsText("Please fix Monday");
        assertReadable(".detail-value");
        assertReadable(".approval-employee");
        assertNoHorizontalOverflow();
        screenshot("details-" + viewport.name());

        page.getByTestId("timesheet-row").nth(1).getByTestId("open-timesheet").click();
        page.locator(".day-row").first().waitFor();
        assertThat(page.getByTestId("status-text")).containsText("Rejected on");
        assertThat(page.getByTestId("decide")).hasCount(0);

        page.getByTestId("back").click();
        assertThat(page.getByTestId("employee-timesheets")).isVisible();
    }

    /** The row of the employee with this name (other rows may mention the name as their manager). */
    private com.microsoft.playwright.Locator rowOf(String name) {
        return page.getByTestId("manage-row").filter(new com.microsoft.playwright.Locator.FilterOptions()
                .setHas(page.locator(".approval-employee").getByText(name, new com.microsoft.playwright.Locator.GetByTextOptions()
                        .setExact(true))));
    }

    @Test
    void anEmployeeWithoutTimesheetsSaysSo() {
        signInAndOpenList(DESKTOP);
        rowOf("Bob Manager").getByTestId("employee-details").click();

        assertThat(page.getByTestId("no-timesheets")).hasText("No timesheets found for this employee.");
        assertReadable(".time-empty");
        page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName("Back to employee management")).click();
        assertThat(page.getByTestId("employees")).isVisible();
    }
}
