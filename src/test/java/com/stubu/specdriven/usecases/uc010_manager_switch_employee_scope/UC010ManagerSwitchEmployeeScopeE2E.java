package com.stubu.specdriven.usecases.uc010_manager_switch_employee_scope;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import com.stubu.specdriven.timesheet.TimesheetService;
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
 * UC-010 Manager Switch Between Direct Reports and Department Employees in a real browser: how the scope switcher
 * looks and behaves on the Employees and Approvals pages at the supported screen sizes. The behaviour behind it is
 * covered by {@code UC010ManagerSwitchEmployeeScope}.
 */
class UC010ManagerSwitchEmployeeScopeE2E extends E2ETest {

    private static final String BOB = "bob.manager@example.com";

    @Autowired
    TimesheetService timesheetService;
    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    /** Bob manages Alice; Dora, in the same department, reports to Carol; both submitted September; Frank is elsewhere. */
    @BeforeEach
    void setUpPeople() {
        jdbc.update("delete from timesheet");
        Employee carol = employee("carol.admin@example.com", "Carol", "Admin", Role.ADMIN, true);
        Employee bob = employee(BOB, "Bob", "Manager", Role.MANAGER, true);
        Employee alice = employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true);
        Employee dora = employee("dora.colleague@example.com", "Dora", "Colleague", Role.EMPLOYEE, true);
        Employee frank = employee("frank.other@example.com", "Frank", "Other", Role.EMPLOYEE, true);
        if (jdbc.queryForObject("select count(*) from department", Integer.class) < 2) {
            jdbc.update("insert into department (name, created_at) values ('Sales', current_timestamp)");
        }
        // Only these five people exist for this test: everybody else is moved out of Bob's department.
        jdbc.update("update employee set department_id = (select max(id) from department), manager_id = null "
                + "where email not in (?, ?, ?, ?, ?)", "carol.admin@example.com", BOB, ALICE,
                "dora.colleague@example.com", "frank.other@example.com");
        jdbc.update("update employee set department_id = (select min(id) from department) where id in (?, ?, ?, ?)",
                carol.getId(), bob.getId(), alice.getId(), dora.getId());
        jdbc.update("update employee set department_id = (select max(id) from department) where id = ?",
                frank.getId());
        jdbc.update("update employee set manager_id = ? where id = ?", bob.getId(), alice.getId());
        jdbc.update("update employee set manager_id = ? where id in (?, ?)", carol.getId(), dora.getId(),
                frank.getId());
        submitted(alice.getId());
        submitted(dora.getId());
    }

    private void submitted(long employeeId) {
        long id = timesheetService.getOrCreate(employeeId, YearMonth.of(2026, 9)).getId();
        jdbc.update("update timesheet set status = 'SUBMITTED', submitted_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")), id);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theSwitcherShowsTheChoiceAndChangesTheList(Viewport viewport) {
        open(viewport);
        signIn(BOB);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/employees"));
        page.getByTestId("employees").waitFor();

        assertThat(page.getByTestId("scope")).containsText("Direct reports only (1)");
        assertThat(page.getByTestId("scope")).containsText("All department employees (3)");
        assertThat(page.getByTestId("employee-row")).hasCount(1);
        assertReadable(".scope-choice");
        assertNoHorizontalOverflow();
        screenshot("scope-direct-" + viewport.name());

        page.getByText("All department employees").click();

        assertThat(page.getByTestId("employee-row")).hasCount(3);
        assertThat(page.getByTestId("employees")).containsText("Dora Colleague");
        assertThat(page.getByTestId("employees")).not().containsText("Frank Other");
        assertNoHorizontalOverflow();
        screenshot("scope-department-" + viewport.name());
    }

    @Test
    void theChosenScopeAlsoAppliesToThePendingApprovals() {
        open(DESKTOP);
        signIn(BOB);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/approvals"));
        page.getByTestId("approvals").waitFor();
        assertThat(page.getByTestId("approval-row")).hasCount(1);

        page.getByText("All department employees").click();

        assertThat(page.getByTestId("approval-row")).hasCount(2);
        assertThat(page.getByTestId("approvals")).containsText("Dora Colleague");

        page.getByTestId("nav-employees").click();
        page.getByTestId("employees").waitFor();

        assertThat(page.getByTestId("employee-row")).hasCount(3);
        assertTrue(page.getByLabel("All department employees (3)").isChecked(), "The choice is remembered");
    }
}
