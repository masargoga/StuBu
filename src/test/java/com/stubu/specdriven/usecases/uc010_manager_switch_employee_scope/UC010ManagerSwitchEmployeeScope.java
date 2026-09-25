package com.stubu.specdriven.usecases.uc010_manager_switch_employee_scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static com.stubu.specdriven.testsupport.ViewTexts.text;
import static com.stubu.specdriven.testsupport.ViewTexts.normalize;

import com.stubu.specdriven.approval.ApprovalsView;
import com.stubu.specdriven.approval.EmployeesView;
import com.stubu.specdriven.approval.PendingApproval;
import com.stubu.specdriven.approval.ReviewScope;
import com.stubu.specdriven.approval.ReviewScopePreference;
import com.stubu.specdriven.approval.ScopeOptions;
import com.stubu.specdriven.approval.TimesheetReviewService;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * UC-010 Manager Switch Between Direct Reports and Department Employees, as the manager Bob. Alice reports to Bob;
 * Dora and Gina work in his department without reporting to him; Frank works in another department. Alice and
 * Dora have submitted timesheets.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee(email = "bob.manager@example.com", firstName = "Bob", lastName = "Manager", role = Role.MANAGER)
class UC010ManagerSwitchEmployeeScope extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Autowired
    MutableClock clock;
    @Autowired
    TimeEntryService entries;
    @Autowired
    TimesheetReviewService reviews;
    @Autowired
    TimesheetService timesheetService;
    @MockitoSpyBean
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    JavaMailSender mailSender;

    private long bob;
    private long carol;
    private long alice;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(employees);
        clock.set(NOW);
        jdbc.update("delete from time_entry");
        jdbc.update("delete from timesheet");
        Department engineering = departments.findAll().stream().findFirst().orElseThrow();
        Department sales = departments.findAll().stream().filter(d -> "Sales".equals(d.getName())).findFirst()
                .orElseGet(() -> departments.save(new Department("Sales")));
        carol = person("carol.admin@example.com", "Carol", "Admin", Role.ADMIN, engineering, null);
        bob = person("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, engineering, carol);
        alice = person("alice.employee@example.com", "Alice", "Employee", Role.EMPLOYEE, engineering, bob);
        long dora = person("dora.colleague@example.com", "Dora", "Colleague", Role.EMPLOYEE, engineering, carol);
        person("gina.new@example.com", "Gina", "New", Role.EMPLOYEE, engineering, carol);
        long erin = person("erin.manager@example.com", "Erin", "Manager", Role.MANAGER, sales, carol);
        person("frank.sales@example.com", "Frank", "Sales", Role.EMPLOYEE, sales, erin);
        // Everybody else who other tests left in Bob's department reports to Carol, so the counts are known.
        jdbc.update("update employee set manager_id = ? where department_id = ? and manager_id = ? and id <> ?",
                carol, engineering.getId(), bob, alice);
        jdbc.update("update employee set department_id = ? where department_id = ? and email not in (?, ?, ?, ?, ?)",
                sales.getId(), engineering.getId(), "carol.admin@example.com", "bob.manager@example.com",
                "alice.employee@example.com", "dora.colleague@example.com", "gina.new@example.com");
        submitted(alice);
        submitted(dora);
        ReviewScopePreference.set(ReviewScope.DIRECT_REPORTS);
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(employees);
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_theDefaultScopeIsDirectReportsAndTheSwitcherShowsWhatEachScopeCovers() {
        ApprovalsView view = openApprovals();

        RadioButtonGroup<ReviewScope> switcher = switcher();
        assertEquals(ReviewScope.DIRECT_REPORTS, switcher.getValue(), "BR-01: direct reports by default");
        assertTrue(text(view).contains("Direct reports only (1)"), text(view));
        assertTrue(text(view).contains("All department employees (4)"), text(view)); // Alice, Carol, Dora, Gina
        assertEquals(List.of("Alice Employee"), pendingNames());
        assertTrue(switcher.getItemEnabledProvider().test(ReviewScope.DEPARTMENT));
    }

    @Test
    void mainFlow_switchingToTheDepartmentShowsItsEmployeesAndTheirPendingTimesheets() {
        ApprovalsView view = openApprovals();

        switcher().setValue(ReviewScope.DEPARTMENT);

        assertEquals(ReviewScope.DEPARTMENT, switcher().getValue(), "The choice stays visible");
        assertEquals(List.of("Alice Employee", "Dora Colleague"), pendingNames().stream().sorted().toList());
        assertFalse(text(view).contains("Frank Sales"));

        switcher().setValue(ReviewScope.DIRECT_REPORTS);
        assertEquals(List.of("Alice Employee"), pendingNames());
    }

    @Test
    void mainFlow_theEmployeeListFollowsTheScopeToo() {
        EmployeesView list = openEmployees();
        assertEquals(List.of("Alice Employee"), employeeNames());

        switcher().setValue(ReviewScope.DEPARTMENT);

        assertEquals(List.of("Carol Admin", "Dora Colleague", "Alice Employee", "Gina New"), employeeNames());
        assertFalse(text(list).contains("Frank Sales"), "BR-03: only employees of the selected scope");
    }

    @Test
    void mainFlow_theScopeIsRememberedForTheSessionAcrossPages() {
        openApprovals();
        switcher().setValue(ReviewScope.DEPARTMENT);

        openEmployees();

        assertEquals(ReviewScope.DEPARTMENT, switcher().getValue());
        assertEquals(4, employeeNames().size());
        openApprovals();
        assertEquals(ReviewScope.DEPARTMENT, switcher().getValue());
        assertEquals(2, pendingNames().size());
    }

    // --- AF-1: No direct reports ----------------------------------------------------------------

    @Test
    void af1_withoutDirectReportsTheManagerIsToldAndCanStillUseTheDepartmentScope() {
        jdbc.update("update employee set manager_id = ? where manager_id = ?", carol, bob);

        EmployeesView view = openEmployees();

        assertTrue(text(view).contains("You have no direct reports."), text(view));
        assertTrue(text(view).contains("Direct reports only (0)"), text(view));
        assertTrue(employeeNames().isEmpty());
        assertTrue(switcher().getItemEnabledProvider().test(ReviewScope.DEPARTMENT));

        switcher().setValue(ReviewScope.DEPARTMENT);
        assertEquals(List.of("Carol Admin", "Dora Colleague", "Alice Employee", "Gina New"), employeeNames());
    }

    // --- AF-2: No department --------------------------------------------------------------------

    @Test
    void af2_withoutADepartmentOnlyDirectReportsCanBeChosen() {
        Employee noDepartment = new Employee("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, null);
        ReflectionTestUtils.setField(noDepartment, "id", bob);
        Mockito.doReturn(Optional.of(noDepartment)).when(employees).findById(bob);
        ReviewScopePreference.set(ReviewScope.DEPARTMENT); // an earlier choice cannot be honoured any more

        ApprovalsView view = openApprovals();

        assertTrue(text(view).contains("Department not configured for your account. Contact your administrator."),
                text(view));
        assertFalse(switcher().getItemEnabledProvider().test(ReviewScope.DEPARTMENT));
        assertEquals(ReviewScope.DIRECT_REPORTS, switcher().getValue());
        assertEquals(new ScopeOptions(1, 0, false), reviews.scopeOptions(bob));
        assertEquals(List.of("Alice Employee"), pendingNames());
    }

    // --- AF-3: Database error -------------------------------------------------------------------

    @Test
    void af3_databaseErrorWhileSwitchingKeepsTheCurrentScope() {
        EmployeesView view = openEmployees();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(employees)
                .findByDepartmentId(any());

        switcher().setValue(ReviewScope.DEPARTMENT);

        assertTrue(text(view).contains("Unable to load employees. Please try again."), text(view));
        assertEquals(List.of("Alice Employee"), employeeNames(), "The previous list stays");
        assertEquals(ReviewScope.DIRECT_REPORTS, ReviewScopePreference.get(), "The previous scope stays active");

        Mockito.reset(employees);
        switcher().setValue(ReviewScope.DEPARTMENT); // try again
        assertEquals(4, employeeNames().size());
        assertFalse(text(view).contains("Unable to load employees"), text(view));
    }

    @Test
    void af3_databaseErrorOnTheApprovalsPageKeepsTheCurrentScopeToo() {
        ApprovalsView view = openApprovals();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(employees)
                .findByDepartmentId(any());

        switcher().setValue(ReviewScope.DEPARTMENT);

        assertTrue(text(view).contains("Unable to load employees. Please try again."), text(view));
        assertEquals(List.of("Alice Employee"), pendingNames());
        assertEquals(ReviewScope.DIRECT_REPORTS, ReviewScopePreference.get());
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br02_theDepartmentScopeIncludesEverybodyOfTheDepartmentNotOnlyDirectReports() {
        ScopeOptions options = reviews.scopeOptions(bob);

        assertEquals(1, options.directReports());
        assertEquals(4, options.departmentEmployees(), "Alice, Carol, Dora and Gina; Frank works in Sales");
        assertTrue(options.departmentAvailable());
    }

    @Test
    void br05_switchingKeepsWhatTheManagerWasDoing() {
        EmployeesView view = openEmployees();
        switcher().setValue(ReviewScope.DEPARTMENT);
        TextField search = find(TextField.class).single();
        test(search).setValue("dora");
        assertEquals(List.of("Dora Colleague"), employeeNames());

        switcher().setValue(ReviewScope.DIRECT_REPORTS);

        assertEquals("dora", find(TextField.class).single().getValue(), "The search is not lost");
        assertTrue(employeeNames().isEmpty(), "Dora is not a direct report: nothing matches in this scope");
        assertTrue(text(view).contains("No employees match your search."), text(view));
        switcher().setValue(ReviewScope.DEPARTMENT);
        assertEquals(List.of("Dora Colleague"), employeeNames());
    }

    // --- helpers --------------------------------------------------------------------------------

    private long person(String email, String first, String last, Role role, Department department, Long managerId) {
        Employee employee = employees.findByEmailIgnoreCase(email)
                .orElseGet(() -> new Employee(email, first, last, role, department.getId()));
        employee.setManagerId(managerId);
        employee.setActive(true);
        return employees.save(employee).getId();
    }

    private void submitted(long employeeId) {
        long id = timesheetService.getOrCreate(employeeId, SEPTEMBER).getId();
        jdbc.update("update timesheet set status = 'SUBMITTED', submitted_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")), id);
    }

    private ApprovalsView openApprovals() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login"); // every call builds a fresh page
        UI.getCurrent().navigate(ApprovalsView.class);
        return find(ApprovalsView.class).single();
    }

    private EmployeesView openEmployees() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login");
        UI.getCurrent().navigate(EmployeesView.class);
        return find(EmployeesView.class).single();
    }

    @SuppressWarnings("unchecked")
    private RadioButtonGroup<ReviewScope> switcher() {
        return find(RadioButtonGroup.class).single();
    }

    private List<String> pendingNames() {
        return find(Div.class).all().stream().filter(div -> "approval-row".equals(div.getTestId()))
                .map(row -> text(row).strip().split("\\s{2,}|\\s(?=September)")[0].strip()).toList();
    }

    private List<String> employeeNames() {
        return find(Div.class).all().stream().filter(div -> "employee-row".equals(div.getTestId()))
                .map(row -> firstCell(row)).toList();
    }

    private static String firstCell(Component row) {
        return normalize(text(row.getElement().getChild(0))).strip();
    }

}
