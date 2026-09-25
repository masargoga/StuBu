package com.stubu.specdriven.usecases.uc011_admin_manage_employees;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-011 Administrator Manage Employees in a real browser: the list, the form, the validation messages and the
 * deactivation dialog at the supported screen sizes. The behaviour behind it is covered by
 * {@code UC011AdminManageEmployees}.
 */
class UC011AdminManageEmployeesE2E extends E2ETest {

    private static final String CAROL = "carol.admin@example.com";

    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    @BeforeEach
    void setUpPeople() {
        jdbc.update("delete from employee where email = ? and id not in (select user_id from audit_log where "
                + "user_id is not null) and id not in (select employee_id from time_entry)", "zoe.zimmer@example.com");
        employee(CAROL, "Carol", "Admin", Role.ADMIN, true);
        employee("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, true);
        employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true);
        jdbc.update("update employee set is_active = true where email in (?, ?, ?)", CAROL,
                "bob.manager@example.com", ALICE);
        jdbc.update("update employee set manager_id = (select id from employee where email = ?) where email = ?",
                "bob.manager@example.com", ALICE);
        if (jdbc.queryForObject("select count(*) from department", Integer.class) < 2) {
            jdbc.update("insert into department (name, created_at) values ('Sales', current_timestamp)");
        }
    }

    private void signInAsCarolAndOpenList(Viewport viewport) {
        open(viewport);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/admin/employees"));
        page.getByTestId("employees").waitFor();
    }

    @Test
    void onlyAdministratorsSeeTheEmployeeManagement() {
        open(DESKTOP);
        signIn("bob.manager@example.com");
        page.locator("[data-testid=check-in]").waitFor();
        assertThat(page.getByTestId("nav-approvals")).isVisible();

        page.navigate(url("/admin/employees"));

        assertThat(page.getByTestId("add-employee")).hasCount(0);
        assertThat(page.getByTestId("employees")).hasCount(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theListFitsEveryScreenSize(Viewport viewport) {
        open(viewport);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();
        if (viewport.width() < 1024) {
            page.getByTestId("drawer-toggle").click();
        }
        assertThat(page.getByTestId("nav-employees")).isVisible();
        assertThat(page.getByTestId("nav-approvals")).hasCount(0);
        page.getByTestId("nav-employees").click();
        page.getByTestId("employees").waitFor();

        assertThat(page.getByTestId("employees")).containsText("Alice Employee");
        assertThat(page.getByTestId("employees")).containsText("Bob Manager");
        assertThat(page.getByTestId("add-employee")).isVisible();
        assertReadable(".approval-employee");
        assertNoHorizontalOverflow();
        screenshot("list-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void anEmployeeIsAddedWithTheForm(Viewport viewport) {
        signInAsCarolAndOpenList(viewport);
        page.getByTestId("add-employee").click();
        page.getByTestId("employee-save").waitFor();
        assertInsideViewport("[data-testid=employee-save]", viewport);
        assertInsideViewport("[data-testid=employee-cancel]", viewport);
        screenshot("form-" + viewport.name());

        page.getByTestId("employee-save").click(); // nothing filled in yet

        assertThat(page.locator(".time-dialog-error")).hasText("Please fill in all required fields.");
        assertReadable(".time-dialog-error");
        screenshot("form-errors-" + viewport.name());

        page.locator("[data-testid=employee-email] input").fill("not-an-email");
        page.locator("[data-testid=employee-first-name] input").fill("Zoe");
        page.locator("[data-testid=employee-last-name] input").fill("Zimmer");
        choose("employee-role", "Employee");
        choose("employee-department", "Sales");
        page.getByTestId("employee-save").click();
        assertThat(page.getByText("Please enter a valid email address.")).isVisible();

        page.locator("[data-testid=employee-email] input").fill("zoe.zimmer@example.com");
        page.getByTestId("employee-save").click();

        assertThat(page.locator(".time-message")).hasText("Employee Zoe Zimmer created.");
        assertThat(page.getByTestId("employees")).containsText("Zoe Zimmer");
        assertReadable(".time-message");
        assertNoHorizontalOverflow();
        screenshot("created-" + viewport.name());
    }

    @Test
    void aDeactivatedEmployeeStaysInTheListAsInactive() {
        signInAsCarolAndOpenList(DESKTOP);
        Locator alice = page.getByTestId("manage-row").filter(new Locator.FilterOptions().setHasText("Alice Employee"));
        alice.getByTestId("deactivate-employee").click();
        assertThat(page.getByText("Deactivate Alice Employee? This employee will no longer be able to log in."))
                .isVisible();
        assertInsideViewport("[data-testid=deactivate-confirm]", DESKTOP);
        screenshot("deactivate-dialog");

        page.getByTestId("deactivate-confirm").click();

        assertThat(page.locator(".time-message")).hasText("Employee Alice Employee deactivated.");
        assertThat(alice).containsText("Inactive");
        assertThat(alice.getByTestId("deactivate-employee")).hasCount(0);
        screenshot("deactivated");
        jdbc.update("update employee set is_active = true where email = ?", ALICE);
    }

    /** Opens a Select in the form and picks the item with the given text. */
    private void choose(String testId, String item) {
        page.locator("[data-testid=" + testId + "]").click();
        page.getByRole(com.microsoft.playwright.options.AriaRole.OPTION,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName(item).setExact(true)).click();
    }

    private void assertInsideViewport(String selector, Viewport viewport) {
        double[] box = box(selector);
        assertTrue(box[0] >= 0 && box[0] + box[2] <= viewport.width() && box[1] >= 0
                && box[1] + box[3] <= viewport.height(), selector + " must be fully visible: "
                + java.util.Arrays.toString(box));
    }
}
