package com.stubu.specdriven.usecases.uc014_admin_view_all_employee_data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static com.stubu.specdriven.testsupport.ViewTexts.text;

import com.stubu.specdriven.admin.EmployeeDetailView;
import com.stubu.specdriven.admin.EmployeeManagementView;
import com.stubu.specdriven.admin.EmployeeOverviewService;
import com.stubu.specdriven.admin.EmployeeSearch;
import com.stubu.specdriven.admin.EmployeeOverviewService.Query;
import com.stubu.specdriven.admin.EmployeeOverviewService.Sort;
import com.stubu.specdriven.approval.EmployeeTimesheetView;
import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.employee.AdminOnlyException;
import com.stubu.specdriven.employee.Choice;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeAdminService;
import com.stubu.specdriven.employee.EmployeeNotFoundException;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-014 Administrator View All Employee and Timesheet Data, as the administrator Carol. Bob (manager) and Alice
 * (employee, reporting to Bob) work in Engineering; Dave (inactive employee) and Erin (manager) in Sales. Alice
 * signed in on 2026-09-20, Erin on 2026-09-22. Alice has an approved, a rejected and a draft timesheet.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee(email = "carol.admin@example.com", firstName = "Carol", lastName = "Admin", role = Role.ADMIN)
class UC014AdminViewAllEmployeeData extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

    @Autowired
    MutableClock clock;
    @Autowired
    AuditService auditService;
    @Autowired
    EmployeeOverviewService overview;
    @Autowired
    EmployeeAdminService admin;
    @MockitoSpyBean
    EmployeeRepository employees;
    @MockitoSpyBean
    EmployeeSearch employeeSearch;
    @MockitoSpyBean
    TimesheetRepository timesheets;
    @Autowired
    TimesheetService timesheetService;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    JavaMailSender mailSender;

    private long carol;
    private long bob;
    private long alice;
    private long dave;
    private long erin;
    private Department engineering;
    private Department sales;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(employees, timesheets, employeeSearch);
        clock.set(NOW);
        jdbc.update("delete from timesheet");
        engineering = departments.findAll().stream().findFirst().orElseThrow();
        sales = departments.findAll().stream().filter(d -> "Sales".equals(d.getName())).findFirst()
                .orElseGet(() -> departments.save(new Department("Sales")));
        carol = person("carol.admin@example.com", "Carol", "Admin", Role.ADMIN, engineering, null, true);
        bob = person("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, engineering, carol, true);
        alice = person("alice.employee@example.com", "Alice", "Employee", Role.EMPLOYEE, engineering, bob, true);
        dave = person("dave.inactive@example.com", "Dave", "Inactive", Role.EMPLOYEE, sales, bob, false);
        erin = person("erin.manager@example.com", "Erin", "Manager", Role.MANAGER, sales, carol, true);
        jdbc.update("delete from audit_log where action = 'LOGIN_SUCCESS'");
        clock.set(Instant.parse("2026-09-20T08:00:00Z"));
        auditService.recordLoginSuccess(alice);
        clock.set(Instant.parse("2026-09-19T08:00:00Z"));
        auditService.recordLoginSuccess(alice); // an older one: only the latest counts
        clock.set(Instant.parse("2026-09-22T10:30:00Z"));
        auditService.recordLoginSuccess(erin);
        clock.set(NOW);
        sheet(alice, YearMonth.of(2026, 7), "APPROVED", "2026-08-01T10:00:00Z", "approved_at = '2026-08-03T10:00:00Z', "
                + "approved_by = " + bob);
        sheet(alice, YearMonth.of(2026, 8), "REJECTED", "2026-09-01T10:00:00Z", "rejected_at = '2026-09-02T10:00:00Z', "
                + "rejected_by = " + bob + ", rejection_reason = 'Please fix Monday'");
        sheet(alice, YearMonth.of(2026, 9), "DRAFT", null, null);
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(employees, timesheets, employeeSearch);
        jdbc.update("delete from employee where email like 'bulk%@example.com' or email = 'under_score@example.com'");
    }

    // --- Main Flow: View All Employees ----------------------------------------------------------

    @Test
    void mainFlow_listsAllEmployeesActiveAndInactiveWithTheirData() {
        EmployeeManagementView view = openList();

        Div alice = row("Alice Employee");
        String text = text(alice);
        assertTrue(text.contains("alice.employee@example.com") && text.contains("Employee") && text.contains("Engineering")
                && text.contains("Bob Manager") && text.contains("Active"), text);
        assertTrue(text.contains("Sep 20, 2026, 8:00:00 AM") && !text.contains("Sep 19"), "The latest login only: " + text);
        Div dave = row("Dave Inactive");
        assertTrue(text(dave).contains("Inactive") && text(dave).contains("Sales") && text(dave).contains("Never"),
                "Inactive employees are listed, never signed in: " + text(dave));
        assertTrue(text(row("Carol Admin")).contains("No manager") && text(row("Carol Admin")).contains("Administrator"));
        assertTrue(text(view).contains("employees"), text(view));
    }

    @Test
    void mainFlow_canBeSortedByAnyColumnInBothDirections() {
        openList();

        assertOrder("Carol Admin", "Alice Employee"); // by name, last name first: Admin, Employee, Inactive, Manager
        assertOrder("Alice Employee", "Dave Inactive");
        button("sort-direction").click();
        assertOrder("Dave Inactive", "Carol Admin");
        button("sort-direction").click();
        select(Sort.class, "sort-by").setValue(Sort.EMAIL);
        assertOrder("alice.employee@", "bob.manager@");
        button("sort-direction").click();
        assertOrder("bob.manager@", "alice.employee@");
        button("sort-direction").click();
        select(Sort.class, "sort-by").setValue(Sort.ROLE);
        assertOrder("Alice Employee", "Bob Manager"); // employees before managers, managers before administrators
        assertOrder("Bob Manager", "Carol Admin");
        select(Sort.class, "sort-by").setValue(Sort.DEPARTMENT);
        assertOrder("Alice Employee", "Dave Inactive"); // Engineering before Sales
        select(Sort.class, "sort-by").setValue(Sort.MANAGER);
        assertOrder("Alice Employee", "Erin Manager"); // Bob Manager before Carol Admin
        select(Sort.class, "sort-by").setValue(Sort.STATUS);
        assertOrder("Alice Employee", "Dave Inactive"); // active first
        select(Sort.class, "sort-by").setValue(Sort.LAST_LOGIN);
        assertOrder("Dave Inactive", "Alice Employee"); // never, then the older login
        assertOrder("Alice Employee", "Erin Manager");
        button("sort-direction").click();
        assertOrder("Erin Manager", "Alice Employee"); // newest first
    }

    @Test
    void mainFlow_theSearchLooksAtNameEmailAndDepartmentIgnoringCase() {
        openList();

        field("employee-search").getElement().setProperty("value", "");
        test(field("employee-search")).setValue("ALICE");
        assertEquals(List.of("Alice Employee"), namesShown());

        test(field("employee-search")).setValue("erin.manager@");
        assertEquals(List.of("Erin Manager"), namesShown());

        test(field("employee-search")).setValue("sAlEs");
        assertTrue(namesShown().containsAll(List.of("Dave Inactive", "Erin Manager")) && !namesShown().contains(
                "Alice Employee"), "By department: " + namesShown());

        test(field("employee-search")).setValue("employee");
        assertTrue(namesShown().contains("Alice Employee"), "By name or email: " + namesShown());
    }

    @Test
    void mainFlow_filtersByStatusRoleAndDepartmentCanBeCombined() {
        openList();

        select(Boolean.class, "filter-status").setValue(false);
        assertTrue(namesShown().contains("Dave Inactive") && !namesShown().contains("Alice Employee"),
                namesShown().toString());

        select(Boolean.class, "filter-status").setValue(true);
        select(Role.class, "filter-role").setValue(Role.MANAGER);
        assertTrue(namesShown().containsAll(List.of("Bob Manager", "Erin Manager")) && !namesShown().contains(
                "Carol Admin") && !namesShown().contains("Alice Employee"), namesShown().toString());

        select(Choice.class, "filter-department").setValue(new Choice(sales.getId(), sales.getName()));
        assertEquals(List.of("Erin Manager"), namesShown().stream().filter(name -> List.of("Bob Manager", "Erin Manager")
                .contains(name)).toList(), "Active managers of Sales");
        assertTrue(!namesShown().contains("Bob Manager"));

        select(Boolean.class, "filter-status").clear();
        select(Role.class, "filter-role").clear();
        select(Choice.class, "filter-department").clear();
        assertTrue(namesShown().containsAll(List.of("Alice Employee", "Dave Inactive", "Carol Admin")));
    }

    @Test
    void mainFlow_aLongListIsShownPageByPageInTheChosenOrder() {
        for (int i = 1; i <= 30; i++) {
            person("bulk%02d@example.com".formatted(i), "Bulk%02d".formatted(i), "Zed", Role.EMPLOYEE, engineering, null, true);
        }
        var first = overview.page(carol, Query.ALL, 0);
        assertEquals(EmployeeOverviewService.PAGE_SIZE, first.rows().size());
        assertTrue(first.pages() >= 2 && first.total() >= 35, first.toString());

        EmployeeManagementView view = openList();
        assertEquals(EmployeeOverviewService.PAGE_SIZE, rows().size());
        assertTrue(text(view).contains("Page 1 of " + first.pages()), text(view));
        assertFalse(button("employees-previous").isEnabled());
        test(button("employees-next")).click();
        assertTrue(text(view).contains("Page 2 of " + first.pages()), text(view));
        assertTrue(button("employees-previous").isEnabled());
        assertEquals(first.total() - EmployeeOverviewService.PAGE_SIZE, rows().size() + (first.pages() - 2)
                * (long) EmployeeOverviewService.PAGE_SIZE, "The rest is on the following pages");

        test(field("employee-search")).setValue("bulk");
        assertTrue(text(view).contains("Page 1 of 2"), "A new search starts at the first page: " + text(view));
        assertEquals(EmployeeOverviewService.PAGE_SIZE, rows().size());
    }

    @Test
    void mainFlow_theOrderHoldsAcrossPagesAndAPageBeyondTheEndShowsTheLastPage() {
        for (int i = 1; i <= 30; i++) {
            person("bulk%02d@example.com".formatted(i), "Bulk%02d".formatted(i), "Zed", Role.EMPLOYEE, engineering, null, true);
        }
        Query bulk = new Query("bulk", null, null, null, Sort.EMAIL, false);
        var page1 = overview.page(carol, bulk, 0);
        var page2 = overview.page(carol, bulk, 1);
        assertEquals(30, page1.total());
        assertEquals(25, page1.rows().size());
        assertEquals(5, page2.rows().size());
        assertEquals("bulk30@example.com", page1.rows().get(0).email(), "Descending by email");
        assertEquals("bulk05@example.com", page2.rows().get(0).email());
        assertEquals(page2.rows(), overview.page(carol, bulk, 99).rows(), "Beyond the end: the last page");
        assertEquals(page1.rows(), overview.page(carol, bulk, -3).rows(), "Before the start: the first page");
    }

    @Test
    void mainFlow_percentAndUnderscoreInTheSearchAreOrdinaryCharacters() {
        person("under_score@example.com", "Under", "Score", Role.EMPLOYEE, engineering, null, true);

        assertEquals(1, overview.search(carol, new Query("under_score", null, null, null, Sort.NAME, true)).size());
        assertEquals(0, overview.search(carol, new Query("under%score", null, null, null, Sort.NAME, true)).size());
        assertEquals(0, overview.search(carol, new Query("%", null, null, null, Sort.NAME, true)).size(),
                "A lone % does not match everything");
        assertEquals(1, overview.search(carol, new Query("UNDER_SCORE", null, null, null, Sort.NAME, true)).size());
    }

    // --- Main Flow: View Employee Details and Timesheets ---------------------------------------

    @Test
    void mainFlow_theDetailsShowTheFullRecordAndAllTimesheets() {
        openList();
        test(button(row("Alice Employee"), "employee-details")).click();

        EmployeeDetailView view = find(EmployeeDetailView.class).single();
        String text = text(view);
        assertTrue(text.contains("Alice Employee") && text.contains("Active"), text);
        assertTrue(text.contains("alice.employee@example.com") && text.contains("Engineering")
                && text.contains("Bob Manager") && text.contains("Created") && text.contains("Last login"), text);
        List<Div> rows = timesheetRows();
        assertEquals(3, rows.size(), "All timesheets, whatever the status");
        assertTrue(text(rows.get(0)).contains("September 2026") && text(rows.get(0)).contains("Not submitted"),
                "The latest month first: " + text(rows.get(0)));
        assertTrue(text(rows.get(1)).contains("August 2026") && text(rows.get(1)).contains("Rejected")
                && text(rows.get(1)).contains("Please fix Monday") && text(rows.get(1)).contains("Sep 2, 2026"),
                text(rows.get(1)));
        assertTrue(text(rows.get(2)).contains("July 2026") && text(rows.get(2)).contains("Approved")
                && text(rows.get(2)).contains("Aug 3, 2026"), text(rows.get(2)));
    }

    @Test
    void mainFlow_aTimesheetOpensInTheReadOnlyViewWithDaysTotalsAndHistory() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login");
        UI.getCurrent().navigate(EmployeeDetailView.class, alice);

        test(button(timesheetRows().get(1), "open-timesheet")).click();

        EmployeeTimesheetView view = find(EmployeeTimesheetView.class).single();
        assertTrue(text(view).contains("Alice Employee") && text(view).contains("Rejected on Sep 2, 2026 with reason: "
                + "Please fix Monday"), text(view));
        assertEquals(31, find(Div.class).all().stream().filter(div -> div.hasClassName("day-row")).count(),
                "August has 31 days");
        assertTrue(find(Button.class).all().stream().noneMatch(button -> "edit-entry".equals(button.getTestId())
                || "decide".equals(button.getTestId())), "Read-only, and administrators do not decide");
    }

    // --- AF-1 .. AF-4 ---------------------------------------------------------------------------

    @Test
    void af1_withoutEmployeesThePageSaysSo() {
        Mockito.doReturn(0L).when(employeeSearch).count(any(Query.class));

        EmployeeManagementView view = openList();

        assertTrue(text(view).contains("No employees found."), text(view));
        assertTrue(rows().isEmpty());
    }

    @Test
    void af2_searchesAndFiltersWithoutMatchesSaySo() {
        EmployeeManagementView view = openList();

        test(field("employee-search")).setValue("nobody with this name");

        assertTrue(text(view).contains("No employees match your filters."), text(view));
        assertFalse(text(view).contains("No employees found."), "There are employees, just none that match");
        assertTrue(rows().isEmpty());
    }

    @Test
    void af3_anEmployeeWithoutTimesheetsSaysSo() {
        jdbc.update("delete from timesheet");
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login");
        UI.getCurrent().navigate(EmployeeDetailView.class, bob);

        EmployeeDetailView view = find(EmployeeDetailView.class).single();

        assertTrue(text(view).contains("No timesheets found for this employee."), text(view));
        assertTrue(text(view).contains("Bob Manager"), "The record is shown anyway");
        assertTrue(timesheetRows().isEmpty());
    }

    @Test
    void af4_databaseErrorShowsAnErrorAndARetry() {
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(employeeSearch)
                .count(any(Query.class));

        EmployeeManagementView view = openList();

        assertTrue(text(view).contains("Unable to load data. Please try again."), text(view));
        assertTrue(button("retry").isVisible());
        assertTrue(rows().isEmpty());

        Mockito.reset(employeeSearch);
        test(button("retry")).click();

        assertFalse(text(view).contains("Unable to load data"), text(view));
        assertFalse(rows().isEmpty());
    }

    @Test
    void af4_databaseErrorOnTheDetailsPageShowsAnErrorAndARetry() {
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timesheets)
                .findByEmployeeIdOrderByYearDescMonthDesc(any());
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login");
        UI.getCurrent().navigate(EmployeeDetailView.class, alice);
        EmployeeDetailView view = find(EmployeeDetailView.class).single();

        assertTrue(text(view).contains("Unable to load data. Please try again."), text(view));

        Mockito.reset(timesheets);
        test(button("retry")).click();
        assertEquals(3, timesheetRows().size());
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_onlyActiveAdministratorsSeeAllEmployeeData() {
        for (long notAdmin : List.of(bob, alice)) {
            assertThrows(AdminOnlyException.class, () -> overview.search(notAdmin, Query.ALL));
            assertThrows(AdminOnlyException.class, () -> overview.details(notAdmin, alice));
        }
        jdbc.update("update employee set is_active = false where id = ?", carol);
        try {
            assertThrows(AdminOnlyException.class, () -> overview.search(carol, Query.ALL));
        } finally {
            jdbc.update("update employee set is_active = true where id = ?", carol);
        }
        assertThrows(EmployeeNotFoundException.class, () -> overview.details(carol, 999_999L));
    }

    @Test
    void br02_bothActiveAndInactiveEmployeesAreVisibleButCanBeFiltered() {
        List<EmployeeRow> all = overview.search(carol, Query.ALL);
        assertTrue(all.stream().anyMatch(row -> row.id() == dave && !row.active()));
        assertTrue(all.stream().anyMatch(row -> row.id() == alice && row.active()));
        assertTrue(overview.search(carol, new Query(null, false, null, null, Sort.NAME, true)).stream()
                .noneMatch(EmployeeRow::active));
        assertTrue(overview.search(carol, new Query(null, true, null, null, Sort.NAME, true)).stream()
                .allMatch(EmployeeRow::active));
    }

    @Test
    void br03_allTimesheetsOfAnEmployeeAreVisibleInEveryStatus() {
        sheet(alice, YearMonth.of(2026, 6), "SUBMITTED", "2026-07-01T10:00:00Z", null);

        var details = overview.details(carol, alice);

        assertEquals(List.of(TimesheetStatus.DRAFT, TimesheetStatus.REJECTED, TimesheetStatus.APPROVED,
                TimesheetStatus.SUBMITTED), details.timesheets().stream().map(line -> line.status()).toList());
        assertEquals(Instant.parse("2026-09-02T10:00:00Z"), details.timesheets().get(1).decidedAt());
        assertEquals("Please fix Monday", details.timesheets().get(1).rejectionReason());
        assertEquals(Instant.parse("2026-09-20T08:00:00Z"), details.employee().lastLoginAt());
        assertEquals(null, overview.details(carol, bob).employee().lastLoginAt(), "Bob never signed in");
    }

    @Test
    void br04_theViewsAreReadOnly() {
        openList();
        test(button(row("Alice Employee"), "employee-details")).click();

        assertTrue(find(Button.class).all().stream().noneMatch(button -> List.of("edit-employee",
                "deactivate-employee", "add-employee", "approve", "reject").contains(String.valueOf(
                        button.getTestId()))), "The details page offers no changes");
    }

    // --- helpers --------------------------------------------------------------------------------

    private long person(String email, String first, String last, Role role, Department department, Long managerId,
            boolean active) {
        Employee employee = employees.findByEmailIgnoreCase(email)
                .orElseGet(() -> new Employee(email, first, last, role, department.getId()));
        employee.setFirstName(first);
        employee.setLastName(last);
        employee.setRole(role);
        employee.setDepartmentId(department.getId());
        employee.setManagerId(managerId);
        employee.setActive(active);
        return employees.save(employee).getId();
    }

    private void sheet(long employeeId, YearMonth month, String status, String submittedAt, String more) {
        long id = timesheetService.getOrCreate(employeeId, month).getId();
        jdbc.update("update timesheet set status = ?, submitted_at = ? where id = ?", status,
                submittedAt == null ? null : Timestamp.from(Instant.parse(submittedAt)), id);
        if (more != null) {
            jdbc.update("update timesheet set " + more + " where id = ?", id);
        }
    }

    private EmployeeManagementView openList() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login"); // every call builds a fresh page
        UI.getCurrent().navigate(EmployeeManagementView.class);
        return find(EmployeeManagementView.class).single();
    }

    private List<Div> rows() {
        return find(Div.class).all().stream().filter(div -> "manage-row".equals(div.getTestId())).toList();
    }

    private Div row(String name) {
        return rows().stream().filter(div -> text(div).contains(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No row for " + name));
    }

    private List<String> namesShown() {
        return rows().stream().map(row -> text(row.getElement().getChild(0)).strip()).toList();
    }

    /** {@code first} appears in the list above {@code second} (matched by name or email start). */
    private void assertOrder(String first, String second) {
        // A row is recognised by its name and email cells, not by the manager column that may mention others.
        List<String> texts = rows().stream().map(row -> text(row.getElement().getChild(0)) + " "
                + text(row.getElement().getChild(1))).toList();
        int a = indexOf(texts, first);
        int b = indexOf(texts, second);
        assertTrue(a >= 0 && b >= 0 && a < b, first + " before " + second + " in " + texts);
    }

    private static int indexOf(List<String> texts, String part) {
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).contains(part)) {
                return i;
            }
        }
        return -1;
    }

    private List<Div> timesheetRows() {
        return find(Div.class).all().stream().filter(div -> "timesheet-row".equals(div.getTestId())).toList();
    }

    private TextField field(String testId) {
        return find(TextField.class).all().stream().filter(field -> testId.equals(field.getTestId())).findFirst()
                .orElseThrow(() -> new AssertionError("No field " + testId));
    }

    @SuppressWarnings("unchecked")
    private <T> Select<T> select(Class<T> type, String testId) {
        return (Select<T>) find(Select.class).all().stream().filter(select -> testId.equals(select.getTestId()))
                .findFirst().orElseThrow(() -> new AssertionError("No select " + testId));
    }

    private Button button(String testId) {
        return find(Button.class).all().stream().filter(button -> testId.equals(button.getTestId())).findFirst()
                .orElseThrow(() -> new AssertionError("No button " + testId));
    }

    private Button button(Component scope, String testId) {
        return find(Button.class).from(scope).all().stream().filter(button -> testId.equals(button.getTestId()))
                .findFirst().orElseThrow(() -> new AssertionError("No button " + testId));
    }

}
