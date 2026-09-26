package com.stubu.specdriven.usecases.uc009_manager_view_employee_timesheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static com.stubu.specdriven.testsupport.ViewTexts.text;
import static com.stubu.specdriven.testsupport.ViewTexts.normalize;

import com.stubu.specdriven.approval.EmployeeSummary;
import com.stubu.specdriven.approval.EmployeeTimesheetDetails;
import com.stubu.specdriven.approval.EmployeeTimesheetView;
import com.stubu.specdriven.approval.EmployeesView;
import com.stubu.specdriven.approval.ReviewNotAllowedException;
import com.stubu.specdriven.approval.ReviewScope;
import com.stubu.specdriven.approval.TimesheetReviewService;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.monthlytimesheet.TimesheetSubmissionService;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
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
 * UC-009 Manager Views Employee Timesheet and Timeline, as the manager Bob. It is 2026-10-05 09:00 UTC. Bob manages
 * Alice, whose September timesheet was submitted on 2026-10-02; Dora works in Bob's department without reporting to
 * him; Frank works in another department.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee(email = "bob.manager@example.com", firstName = "Bob", lastName = "Manager", role = Role.MANAGER)
class UC009ManagerViewEmployeeTimesheet extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final ZoneId UTC = ZoneOffset.UTC;

    @Autowired
    MutableClock clock;
    @Autowired
    TimeEntryService entries;
    @Autowired
    TimesheetReviewService reviews;
    @Autowired
    TimesheetSubmissionService submissions;
    @Autowired
    TimesheetService timesheetService;
    @MockitoSpyBean
    TimesheetRepository timesheets;
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
    private long dora;
    private long frank;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(timesheets);
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
        long erin = person("erin.manager@example.com", "Erin", "Manager", Role.MANAGER, sales, carol);
        frank = person("frank.sales@example.com", "Frank", "Sales", Role.EMPLOYEE, sales, erin);
        jdbc.update("update employee set manager_id = ? where manager_id = ? and id <> ?", carol, bob, alice); // left over from other tests
        record(alice, "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record(alice, "2026-09-01T13:00:00Z", "2026-09-01T17:00:00Z");
        record(alice, "2026-09-02T09:00:00Z", "2026-09-02T10:30:00Z");
        submitted(alice);
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(timesheets);
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_theManagerPicksAnEmployeeFromTheListAndSeesTheirTimesheet() {
        EmployeesView list = openList();
        assertTrue(text(list).contains("Alice Employee"), text(list));
        assertFalse(text(list).contains("Dora Colleague"), "Only direct reports are listed by default");
        assertFalse(text(list).contains("Frank Sales"), text(list));
        assertEquals(1, employeeRows().size());
        assertTrue(text(employeeRows().getFirst()).contains("Employee") && text(employeeRows().getFirst())
                .contains("alice.employee@example.com"), text(employeeRows().getFirst()));

        test(button(employeeRows().getFirst(), "view-timesheet")).click();

        EmployeeTimesheetView view = find(EmployeeTimesheetView.class).single();
        assertTrue(text(view).contains("Alice Employee"), text(view));
        // The current month (October) has no timesheet yet.
        assertTrue(text(view).contains("No timesheet found for Alice Employee in October 2026."), text(view));
    }

    @Test
    void mainFlow_theTimesheetShowsDaysTotalsStatusAndHistory() {
        jdbc.update("update timesheet set status = 'DRAFT', submitted_at = null");
        submissions.submit(alice, SEPTEMBER, UTC); // so that the audit log has the submission
        EmployeeTimesheetView view = openTimesheet(alice, "2026-09");

        assertTrue(text(view).contains("Alice Employee"), text(view));
        assertEquals("Employee", badgeText("role-badge"));
        assertEquals(YearMonth.of(2026, 9), monthSelect().getValue());
        assertEquals("Submitted", badgeText("timesheet-status"));
        assertTrue(text(view).contains("Submitted on Oct 5, 2026. Awaiting approval."), text(view));
        assertTrue(text(view).contains("Total hours: 9h 30m"), text(view));
        assertTrue(text(view).contains("Break: 1h 0m"), text(view));
        assertEquals(30, dayRows().size());
        assertTrue(text(dayRows().get(0)).contains("Total 8h 0m, break 1h 0m"), text(dayRows().get(0)));
        assertTrue(text(dayRows().get(0)).contains("8:00 AM – 12:00 PM, 4h 0m"), text(dayRows().get(0)));
        assertTrue(text(dayRows().get(2)).contains("No entry"), text(dayRows().get(2)));
        assertTrue(text(view).contains("History"), text(view));
        assertTrue(text(view).contains("Alice Employee submitted the timesheet"), text(view));
        assertFalse(buttons("decide").isEmpty(), "A submitted timesheet can be decided");
        assertFalse(monthSelect().getListDataView().getItems().anyMatch(month -> month.isAfter(YearMonth.of(2026, 10))));
    }

    @Test
    void mainFlow_aDayCanBeOpenedToSeeItsTimeline() {
        EmployeeTimesheetView view = openTimesheet(alice, "2026-09");
        assertEquals(2, buttons("day-details").size(), "Only days with work periods offer details");

        test(buttons("day-details").getFirst()).click();

        Dialog dialog = find(Dialog.class).single();
        assertEquals("Tuesday, September 1, 2026", dialog.getHeaderTitle());
        assertTrue(text(dialog).contains("8:00 AM – 12:00 PM"), text(dialog));
        assertTrue(text(dialog).contains("1:00 PM – 5:00 PM"), text(dialog));
        assertTrue(text(dialog).contains("Total 8h 0m, break 1h 0m"), text(dialog));
        assertTrue(find(Button.class).from(dialog).all().stream()
                .noneMatch(button -> "edit-entry".equals(button.getTestId())
                        || "delete-entry".equals(button.getTestId())), "Read-only");

        test(button(dialog, "day-close")).click();
        assertFalse(dialog.isOpened());
        assertTrue(text(view).contains("Alice Employee"));
    }

    @Test
    void mainFlow_anotherMonthCanBeChosen() {
        record(alice, "2026-08-10T08:00:00Z", "2026-08-10T09:00:00Z");
        timesheetService.getOrCreate(alice, YearMonth.of(2026, 8));
        EmployeeTimesheetView view = openTimesheet(alice, "2026-09");
        assertTrue(button("next-month").isEnabled(), "October is the current month");

        test(button("previous-month")).click();

        assertEquals(YearMonth.of(2026, 8), monthSelect().getValue());
        assertTrue(text(find(EmployeeTimesheetView.class).single()).contains("Total hours: 1h 0m"), text(view));
        assertTrue(text(find(EmployeeTimesheetView.class).single()).contains("Not submitted") || text(find(
                EmployeeTimesheetView.class).single()).contains("has not submitted"));
    }

    // --- AF-1: Not authorized -------------------------------------------------------------------

    @Test
    void af1_anEmployeeOfAnotherDepartmentCannotBeViewed() {
        openTimesheet(frank, "2026-09");

        EmployeesView list = find(EmployeesView.class).single();
        assertTrue(text(list).contains("You do not have permission to view this employee's timesheet."), text(list));
        assertThrows(ReviewNotAllowedException.class, () -> reviews.employeeTimesheet(bob, frank, SEPTEMBER, UTC));
    }

    @Test
    void af1_anEmployeeThatDoesNotExistAndTheManagersOwnTimesheetAreAnsweredTheSameWay() {
        openTimesheet(999_999L, "2026-09");
        assertTrue(text(find(EmployeesView.class).single()).contains("You do not have permission"));
        assertThrows(ReviewNotAllowedException.class, () -> reviews.employeeTimesheet(bob, bob, SEPTEMBER, UTC));
    }

    // --- AF-2: No timesheet ---------------------------------------------------------------------

    @Test
    void af2_aMonthWithoutTimesheetSaysSoAndCreatesNothing() {
        int before = timesheets.findAll().size();

        EmployeeTimesheetView view = openTimesheet(alice, "2026-07");

        assertTrue(text(view).contains("No timesheet found for Alice Employee in July 2026."), text(view));
        assertEquals(before, timesheets.findAll().size(), "Looking at a month never creates a timesheet");
        assertTrue(dayRows().isEmpty());
        assertFalse(reviews.employeeTimesheet(bob, alice, YearMonth.of(2026, 7), UTC).exists());
        assertTrue(monthSelect().isVisible(), "Another month can still be chosen");
    }

    // --- AF-3: No time entries ------------------------------------------------------------------

    @Test
    void af3_aTimesheetWithoutEntriesShowsAnEmptyMonth() {
        timesheetService.getOrCreate(alice, YearMonth.of(2026, 6));

        EmployeeTimesheetView view = openTimesheet(alice, "2026-06");

        assertTrue(text(view).contains("No time entries recorded for June 2026."), text(view));
        assertTrue(text(view).contains("Total hours: 0h 0m"), text(view));
        assertEquals(30, dayRows().size());
    }

    // --- AF-4: Database error -------------------------------------------------------------------

    @Test
    void af4_databaseErrorShowsAnErrorAndARetry() {
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timesheets)
                .findByEmployeeIdAndYearAndMonth(any(), anyInt(), anyInt());

        EmployeeTimesheetView view = openTimesheet(alice, "2026-09");

        assertTrue(text(view).contains("Unable to load timesheet. Please try again."), text(view));
        assertTrue(button("retry").isVisible());
        assertTrue(dayRows().isEmpty());

        Mockito.reset(timesheets);
        test(button("retry")).click();

        assertFalse(text(view).contains("Unable to load timesheet"), text(view));
        assertEquals(30, dayRows().size());
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_aManagerCanAlsoViewColleaguesOfTheirDepartmentAndAnAdministratorEverybody() {
        submitted(dora);
        submitted(frank);

        assertTrue(reviews.employeeTimesheet(bob, dora, SEPTEMBER, UTC).exists());
        assertThrows(ReviewNotAllowedException.class, () -> reviews.employeeTimesheet(bob, frank, SEPTEMBER, UTC));
        assertTrue(reviews.employeeTimesheet(carol, frank, SEPTEMBER, UTC).exists());

        assertEquals(List.of("Alice Employee"), names(reviews.employees(bob, ReviewScope.DIRECT_REPORTS)));
        // Everybody of the department, sorted by last name; other tests may have left more people behind.
        List<String> department = names(reviews.employees(bob, ReviewScope.DEPARTMENT));
        assertTrue(department.containsAll(List.of("Carol Admin", "Dora Colleague", "Alice Employee")), department.toString());
        assertFalse(department.contains("Frank Sales"), department.toString());
        assertTrue(department.indexOf("Carol Admin") < department.indexOf("Dora Colleague")
                && department.indexOf("Dora Colleague") < department.indexOf("Alice Employee"), department.toString());
        assertEquals(List.of(), reviews.employees(alice, ReviewScope.DIRECT_REPORTS));
    }

    @Test
    void br02_timesheetsInEveryStatusCanBeViewed() {
        for (String status : List.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED")) {
            jdbc.update("update timesheet set status = ?, approved_at = ?, approved_by = ?, rejected_at = ?, "
                    + "rejected_by = ?, rejection_reason = ?", status, Timestamp.from(NOW), bob, Timestamp.from(NOW), bob,
                    "REJECTED".equals(status) ? "Please fix Monday" : null);

            EmployeeTimesheetView view = openTimesheet(alice, "2026-09");

            assertNotNull(badgeText("timesheet-status"), status);
            assertEquals(30, dayRows().size(), status);
            assertEquals("SUBMITTED".equals(status), !buttons("decide").isEmpty(), status);
        }
    }

    @Test
    void br03_theViewIsReadOnly() {
        for (String status : List.of("DRAFT", "REJECTED")) { // the statuses in which the employee may edit
            jdbc.update("update timesheet set status = ?", status);
            openTimesheet(alice, "2026-09");

            assertTrue(buttons("edit-entry").isEmpty(), status);
            assertTrue(buttons("delete-entry").isEmpty(), status);
            assertTrue(buttons("submit-timesheet").isEmpty(), status);
        }
    }

    @Test
    void br04_theHistoryShowsSubmissionAndDecisionsWithReasons() {
        reviews.reject(bob, timesheetId(alice), "Please fix Monday");

        EmployeeTimesheetDetails details = reviews.employeeTimesheet(bob, alice, SEPTEMBER, UTC);
        EmployeeTimesheetView view = openTimesheet(alice, "2026-09");

        assertEquals(1, details.history().size(), "The submission was set up directly, only the decision is audited");
        assertEquals("Bob Manager", details.history().getFirst().actorName());
        assertEquals("Please fix Monday", details.history().getFirst().reason());
        assertTrue(text(view).contains("Bob Manager rejected the timesheet"), text(view));
        assertTrue(text(view).contains("Note: Please fix Monday"), text(view));
    }

    @Test
    void theEmployeeListCanBeSearchedAndSaysWhenThereAreNoDirectReports() {
        EmployeesView list = openList();
        TextField search = find(TextField.class).single();

        test(search).setValue("zzz");
        assertTrue(text(list).contains("No employees match your search."), text(list));
        assertTrue(employeeRows().isEmpty());

        test(search).setValue("ALICE");
        assertEquals(1, employeeRows().size());
        test(search).setValue("alice.employee@");
        assertEquals(1, employeeRows().size(), "The email address is searched too");

        jdbc.update("update employee set manager_id = ? where manager_id = ?", carol, bob);
        EmployeesView empty = openList();
        assertTrue(text(empty).contains("You have no direct reports."), text(empty));
        jdbc.update("update employee set manager_id = ? where id = ?", bob, alice);
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

    /** The employee's September timesheet as SUBMITTED, as UC-006 would have left it. */
    private long submitted(long employeeId) {
        long id = timesheetService.getOrCreate(employeeId, SEPTEMBER).getId();
        jdbc.update("update timesheet set status = 'SUBMITTED', submitted_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")), id);
        return id;
    }

    private long timesheetId(long employeeId) {
        return timesheetService.getOrCreate(employeeId, SEPTEMBER).getId();
    }

    private static List<String> names(List<EmployeeSummary> employees) {
        return employees.stream().map(EmployeeSummary::fullName).toList();
    }

    private EmployeesView openList() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login"); // every call builds a fresh page
        UI.getCurrent().navigate(EmployeesView.class);
        return find(EmployeesView.class).single();
    }

    private EmployeeTimesheetView openTimesheet(long employeeId, String month) {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login");
        UI.getCurrent().navigate(EmployeeTimesheetView.class, employeeId, QueryParameters.of("month", month));
        return find(EmployeeTimesheetView.class).all().stream().findFirst().orElse(null);
    }

    private String badgeText(String testId) {
        return find(com.vaadin.flow.component.badge.Badge.class).all().stream()
                .filter(badge -> testId.equals(badge.getTestId())).map(badge -> normalize(badge.getText())).findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Select<YearMonth> monthSelect() {
        return find(Select.class).all().stream().filter(select -> !(select instanceof com.stubu.specdriven.base.LanguageSelector))
                .reduce((first, second) -> { throw new AssertionError("More than one month selector"); }).orElseThrow();
    }

    private List<Div> employeeRows() {
        return find(Div.class).all().stream().filter(div -> "employee-row".equals(div.getTestId())).toList();
    }

    private List<Div> dayRows() {
        return find(Div.class).all().stream().filter(div -> div.hasClassName("day-row")).toList();
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

}
