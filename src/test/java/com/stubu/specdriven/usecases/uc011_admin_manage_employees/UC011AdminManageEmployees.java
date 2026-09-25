package com.stubu.specdriven.usecases.uc011_admin_manage_employees;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static com.stubu.specdriven.testsupport.ViewTexts.text;

import com.stubu.specdriven.admin.EmployeeManagementView;
import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditLogEntry;
import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.employee.AdminOnlyException;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.DeactivationRefusedException;
import com.stubu.specdriven.employee.EditConflictException;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeAdminService;
import com.stubu.specdriven.employee.EmployeeInput;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.employee.EmployeeValidationException;
import com.stubu.specdriven.employee.EmployeeValidationException.Problem;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-011 Administrator Manage Employees, as the administrator Carol. Bob manages Alice; Dora is a second manager.
 * The departments are Engineering and Sales.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithEmployee(email = "carol.admin@example.com", firstName = "Carol", lastName = "Admin", role = Role.ADMIN)
class UC011AdminManageEmployees extends SpringBrowserlessTest {

    private static final String NEW_EMAIL = "zoe.zimmer@example.com";

    @Autowired
    EmployeeAdminService admin;
    @Autowired
    com.stubu.specdriven.admin.EmployeeOverviewService overview;
    @MockitoSpyBean
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    JdbcTemplate jdbc;

    private long carol;
    private long bob;
    private long alice;
    private Department engineering;
    private Department sales;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(employees);
        engineering = departments.findAll().stream().findFirst().orElseThrow();
        sales = departments.findAll().stream().filter(d -> "Sales".equals(d.getName())).findFirst()
                .orElseGet(() -> departments.save(new Department("Sales")));
        carol = person("carol.admin@example.com", "Carol", "Admin", Role.ADMIN, null, true);
        bob = person("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, carol, true);
        alice = person("alice.employee@example.com", "Alice", "Employee", Role.EMPLOYEE, bob, true);
        // Other tests may have left people behind: remove the one this test creates, and keep it reproducible.
        jdbc.update("delete from employee where email in (?, ?) and id not in (select user_id from audit_log "
                + "where user_id is not null) and id not in (select employee_id from time_entry)", NEW_EMAIL,
                "yan.young@example.com");
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(employees);
    }

    // --- Main Flow: Create ----------------------------------------------------------------------

    @Test
    void mainFlow_create_addsAnActiveEmployeeAndAuditsIt() {
        EmployeeManagementView view = openList();
        test(button("add-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        assertEquals("Email", textField(dialog, "employee-email").getLabel());
        assertEquals("Department", select(dialog, "employee-department", Object.class).getLabel());
        fill(dialog, NEW_EMAIL, "Zoe", "Zimmer", "Manager", "Sales", "Bob Manager");
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button(dialog, "employee-save")).click();

        assertFalse(dialog.isOpened());
        Employee created = employees.findByEmailIgnoreCase(NEW_EMAIL).orElseThrow();
        assertTrue(created.isActive(), "Active by default");
        assertEquals("Zoe", created.getFirstName());
        assertEquals("Zimmer", created.getLastName());
        assertEquals(Role.MANAGER, created.getRole());
        assertEquals(sales.getId(), created.getDepartmentId());
        assertEquals(bob, created.getManagerId());
        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.CREATE, entry.getAction());
        assertEquals(carol, entry.getUserId());
        assertEquals("Employee", entry.getEntityType());
        assertEquals(created.getId(), entry.getEntityId());
        assertTrue(entry.getNewValues().contains("\"email\":\"" + NEW_EMAIL + "\""), entry.getNewValues());
        assertTrue(entry.getNewValues().contains("\"role\":\"MANAGER\"") && entry.getNewValues()
                .contains("\"active\":true"), entry.getNewValues());
        assertTrue(text(view).contains("Employee Zoe Zimmer created."), text(view));
        assertTrue(rowFor("Zoe Zimmer") != null, "Back in the list");
        assertTrue(text(rowFor("Zoe Zimmer")).contains("Sales") && text(rowFor("Zoe Zimmer")).contains("Bob Manager"),
                text(rowFor("Zoe Zimmer")));
    }

    @Test
    void mainFlow_create_theManagerIsOptional() {
        openList();
        test(button("add-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        fill(dialog, NEW_EMAIL, "Zoe", "Zimmer", "Employee", "Engineering", null);

        test(button(dialog, "employee-save")).click();

        assertNull(employees.findByEmailIgnoreCase(NEW_EMAIL).orElseThrow().getManagerId(), "BR-03");
        assertTrue(text(rowFor("Zoe Zimmer")).contains("No manager"), text(rowFor("Zoe Zimmer")));
    }

    // --- Main Flow: Edit ------------------------------------------------------------------------

    @Test
    void mainFlow_edit_showsTheCurrentDataKeepsTheEmailAndAuditsOldAndNewValues() {
        openList();
        test(button(rowFor("Alice Employee"), "edit-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        assertEquals("alice.employee@example.com", textField(dialog, "employee-email").getValue());
        assertTrue(textField(dialog, "employee-email").isReadOnly(), "BR-07: the email cannot be changed");
        assertEquals("Alice", textField(dialog, "employee-first-name").getValue());
        assertEquals(Role.EMPLOYEE, select(dialog, "employee-role", Role.class).getValue());
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(textField(dialog, "employee-first-name")).setValue("Alicia");
        test(select(dialog, "employee-role", Role.class)).selectItem("Manager");
        test(select(dialog, "employee-department", Object.class)).selectItem("Sales");
        test(button(dialog, "employee-save")).click();

        assertFalse(dialog.isOpened());
        Employee updated = employees.findById(alice).orElseThrow();
        assertEquals("Alicia", updated.getFirstName());
        assertEquals(Role.MANAGER, updated.getRole());
        assertEquals(sales.getId(), updated.getDepartmentId());
        assertEquals("alice.employee@example.com", updated.getEmail());
        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.UPDATE, entry.getAction());
        assertEquals(carol, entry.getUserId());
        assertEquals(alice, entry.getEntityId());
        assertTrue(entry.getOldValues().contains("\"firstName\":\"Alice\"") && entry.getOldValues()
                .contains("\"role\":\"EMPLOYEE\""), entry.getOldValues());
        assertTrue(entry.getNewValues().contains("\"firstName\":\"Alicia\"") && entry.getNewValues()
                .contains("\"role\":\"MANAGER\""), entry.getNewValues());
        assertTrue(text(find(EmployeeManagementView.class).single()).contains("Employee Alicia Employee updated."));
    }

    @Test
    void mainFlow_edit_withoutChangesWritesNoAuditEntry() {
        EmployeeRow row = rowOf(alice);
        int audits = auditLog.findAllByOrderByIdAsc().size();

        admin.update(carol, alice, new EmployeeInput(row.email(), row.firstName(), row.lastName(), row.role(),
                row.managerId(), row.departmentId()));

        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
    }

    // --- Main Flow: Deactivate ------------------------------------------------------------------

    @Test
    void mainFlow_deactivate_marksTheEmployeeInactiveAndKeepsThem() {
        openList();
        test(button(rowFor("Alice Employee"), "deactivate-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        assertTrue(text(dialog).contains("Deactivate Alice Employee? This employee will no longer be able to log in."),
                text(dialog));
        test(textField(dialog, "deactivate-reason")).setValue("Left the company");
        assertTrue(employees.findById(alice).orElseThrow().isActive(), "Nothing happens before the confirmation");
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button(dialog, "deactivate-confirm")).click();

        assertFalse(dialog.isOpened());
        assertFalse(employees.findById(alice).orElseThrow().isActive());
        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.UPDATE, entry.getAction());
        assertEquals("Deactivated: Left the company", entry.getReason());
        assertTrue(entry.getOldValues().contains("\"active\":true"), entry.getOldValues());
        assertTrue(entry.getNewValues().contains("\"active\":false"), entry.getNewValues());
        EmployeeManagementView view = find(EmployeeManagementView.class).single();
        assertTrue(text(view).contains("Employee Alice Employee deactivated."), text(view));
        assertTrue(text(rowFor("Alice Employee")).contains("Inactive"), "Still listed, marked as inactive");
        assertTrue(buttons(rowFor("Alice Employee"), "deactivate-employee").isEmpty());
    }

    // --- AF-1 .. AF-3: Validation ---------------------------------------------------------------

    @Test
    void af1_anEmailThatExistsAlreadyIsRefusedInAnyCase() {
        openList();
        test(button("add-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        fill(dialog, "ALICE.Employee@Example.com", "Zoe", "Zimmer", "Employee", "Sales", null);
        int count = employees.findAll().size();

        test(button(dialog, "employee-save")).click();

        assertTrue(dialog.isOpened(), "The form stays open");
        assertEquals("Email already exists. Please use a different email.", textField(dialog, "employee-email")
                .getErrorMessage());
        assertTrue(textField(dialog, "employee-email").isInvalid());
        assertEquals(count, employees.findAll().size());

        test(textField(dialog, "employee-email")).setValue(NEW_EMAIL); // corrected and resubmitted
        test(button(dialog, "employee-save")).click();
        assertFalse(dialog.isOpened());
        assertTrue(employees.findByEmailIgnoreCase(NEW_EMAIL).isPresent());
    }

    @Test
    void af2_anInvalidEmailIsRefused() {
        for (String email : List.of("no-at-sign.example.com", "a@b", "two@@example.com", "spaces in@example.com", "")) {
            EmployeeValidationException invalid = assertThrows(EmployeeValidationException.class,
                    () -> admin.create(carol, new EmployeeInput(email, "Zoe", "Zimmer", Role.EMPLOYEE, null,
                            sales.getId())), email);
            assertTrue(invalid.has(Problem.EMAIL_INVALID), email);
        }
        openList();
        test(button("add-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        fill(dialog, "missing-at.example.com", "Zoe", "Zimmer", "Employee", "Sales", null);

        test(button(dialog, "employee-save")).click();

        assertEquals("Please enter a valid email address.", textField(dialog, "employee-email").getErrorMessage());
        assertTrue(dialog.isOpened());
    }

    @Test
    void af3_missingRequiredFieldsAreReportedTogether() {
        openList();
        test(button("add-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        test(textField(dialog, "employee-email")).setValue(NEW_EMAIL);

        test(button(dialog, "employee-save")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Please fill in all required fields."), text(dialog));
        assertTrue(textField(dialog, "employee-first-name").isInvalid());
        assertTrue(textField(dialog, "employee-last-name").isInvalid());
        assertTrue(select(dialog, "employee-role", Role.class).isInvalid());
        assertTrue(select(dialog, "employee-department", Object.class).isInvalid());
        assertTrue(employees.findByEmailIgnoreCase(NEW_EMAIL).isEmpty());
    }

    // --- AF-4: Database error -------------------------------------------------------------------

    @Test
    void af4_databaseErrorWhileCreatingKeepsTheFormOpenForARetry() {
        openList();
        test(button("add-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        fill(dialog, NEW_EMAIL, "Zoe", "Zimmer", "Employee", "Sales", null);
        int audits = auditLog.findAllByOrderByIdAsc().size();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(employees)
                .saveAndFlush(any(Employee.class));

        test(button(dialog, "employee-save")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Unable to save changes. Please try again."), text(dialog));
        assertTrue(employees.findByEmailIgnoreCase(NEW_EMAIL).isEmpty());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());

        Mockito.reset(employees);
        test(button(dialog, "employee-save")).click(); // retry
        assertFalse(dialog.isOpened());
        assertTrue(employees.findByEmailIgnoreCase(NEW_EMAIL).isPresent());
    }

    @Test
    void af4_databaseErrorWhileEditingAndDeactivatingChangesNothing() {
        openList();
        test(button(rowFor("Alice Employee"), "edit-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        test(textField(dialog, "employee-first-name")).setValue("Alicia");
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(employees)
                .saveAndFlush(any(Employee.class));

        test(button(dialog, "employee-save")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Unable to save changes. Please try again."), text(dialog));
        assertEquals("Alice", employees.findById(alice).orElseThrow().getFirstName());
        test(button(dialog, "employee-cancel")).click();

        test(button(rowFor("Alice Employee"), "deactivate-employee")).click();
        Dialog confirm = find(Dialog.class).single();
        test(button(confirm, "deactivate-confirm")).click();

        assertTrue(confirm.isOpened(), "A retry is offered");
        assertTrue(text(confirm).contains("Unable to save changes. Please try again."), text(confirm));
        assertTrue(employees.findById(alice).orElseThrow().isActive());
        Mockito.reset(employees);
        test(button(confirm, "deactivate-confirm")).click();
        assertFalse(employees.findById(alice).orElseThrow().isActive());
    }

    // --- AF-5 / AF-6: Cancel --------------------------------------------------------------------

    @Test
    void af5_cancellingTheFormChangesNothing() {
        openList();
        int count = employees.findAll().size();
        int audits = auditLog.findAllByOrderByIdAsc().size();
        test(button("add-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        fill(dialog, NEW_EMAIL, "Zoe", "Zimmer", "Employee", "Sales", null);

        test(button(dialog, "employee-cancel")).click();

        assertFalse(dialog.isOpened());
        assertEquals(count, employees.findAll().size());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
    }

    @Test
    void af6_cancellingTheDeactivationKeepsTheEmployeeActive() {
        openList();
        int audits = auditLog.findAllByOrderByIdAsc().size();
        test(button(rowFor("Alice Employee"), "deactivate-employee")).click();
        Dialog dialog = find(Dialog.class).single();

        test(button(dialog, "deactivate-cancel")).click();

        assertFalse(dialog.isOpened());
        assertTrue(employees.findById(alice).orElseThrow().isActive());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br04_onlyActiveAdministratorsMayManageEmployees() {
        EmployeeInput input = new EmployeeInput(NEW_EMAIL, "Zoe", "Zimmer", Role.EMPLOYEE, null, sales.getId());
        for (long notAdmin : List.of(bob, alice)) {
            assertThrows(AdminOnlyException.class, () -> admin.create(notAdmin, input));
            assertThrows(AdminOnlyException.class, () -> overview.search(notAdmin,
                    com.stubu.specdriven.admin.EmployeeOverviewService.Query.ALL));
            assertThrows(AdminOnlyException.class, () -> admin.update(notAdmin, alice, input));
            assertThrows(AdminOnlyException.class, () -> admin.deactivate(notAdmin, alice, null));
        }
        jdbc.update("update employee set is_active = false where id = ?", carol);
        try {
            assertThrows(AdminOnlyException.class, () -> admin.create(carol, input));
        } finally {
            jdbc.update("update employee set is_active = true where id = ?", carol);
        }
        assertTrue(employees.findByEmailIgnoreCase(NEW_EMAIL).isEmpty());
    }

    @Test
    void br08_aSaveBasedOnAnOutdatedVersionIsRefusedInsteadOfOverwritingTheOtherChange() {
        EmployeeRow opened = rowOf(alice);
        // Another administrator changes Alice after the form was opened.
        admin.update(carol, alice, new EmployeeInput(null, "Alicia", "Employee", Role.EMPLOYEE, bob, engineering.getId()));
        int audits = auditLog.findAllByOrderByIdAsc().size();

        assertThrows(EditConflictException.class, () -> admin.update(carol, alice, new EmployeeInput(null, "Alice",
                "Employee-Smith", Role.EMPLOYEE, bob, engineering.getId(), opened.version())));

        Employee stored = employees.findById(alice).orElseThrow();
        assertEquals("Alicia", stored.getFirstName(), "The other change is still there");
        assertEquals("Employee", stored.getLastName());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size(), "A refused save is not audited");

        EmployeeRow current = rowOf(alice);
        admin.update(carol, alice, new EmployeeInput(null, "Alicia", "Employee-Smith", Role.EMPLOYEE, bob,
                engineering.getId(), current.version()));
        assertEquals("Employee-Smith", employees.findById(alice).orElseThrow().getLastName(),
                "With the current version the change is stored");
    }

    @Test
    void br05_deactivatingKeepsTheRecordAndItsHistory() {
        long entries = jdbc.queryForObject("select count(*) from audit_log where user_id = ?", Long.class, alice);

        admin.deactivate(carol, alice, null);

        assertTrue(employees.findById(alice).isPresent(), "Soft delete: the record stays");
        assertEquals(entries, jdbc.queryForObject("select count(*) from audit_log where user_id = ?", Long.class,
                alice));
        assertEquals("Deactivated", auditLog.findAllByOrderByIdAsc().getLast().getReason());
    }

    @Test
    void br07_theEmailCannotBeChangedByAnUpdate() {
        admin.update(carol, alice, new EmployeeInput("someone.else@example.com", "Alice", "Employee", Role.EMPLOYEE,
                bob, engineering.getId()));

        assertEquals("alice.employee@example.com", employees.findById(alice).orElseThrow().getEmail());
    }

    @Test
    void theManagerHierarchyStaysSensible() {
        EmployeeValidationException self = assertThrows(EmployeeValidationException.class, () -> admin.update(carol,
                bob, new EmployeeInput("x@example.com", "Bob", "Manager", Role.MANAGER, bob, engineering.getId())));
        assertTrue(self.has(Problem.MANAGER_IS_SELF));
        EmployeeValidationException cycle = assertThrows(EmployeeValidationException.class, () -> admin.update(carol,
                bob, new EmployeeInput("x@example.com", "Bob", "Manager", Role.MANAGER, alice, engineering.getId())),
                "Bob manages Alice, so Alice cannot be Bob's manager");
        assertTrue(cycle.has(Problem.MANAGER_CYCLE));
        EmployeeValidationException unknown = assertThrows(EmployeeValidationException.class, () -> admin.create(carol,
                new EmployeeInput(NEW_EMAIL, "Zoe", "Zimmer", Role.EMPLOYEE, 999_999L, sales.getId())));
        assertTrue(unknown.has(Problem.MANAGER_UNKNOWN));
        List<String> candidates = admin.managerCandidates(carol).stream().map(choice -> choice.label()).toList();
        assertTrue(candidates.contains("Bob Manager") && candidates.contains("Carol Admin"), candidates.toString());
        assertFalse(candidates.contains("Alice Employee"), "Only managers and administrators");
    }

    @Test
    void administratorsDoNotLockThemselvesOut() {
        assertEquals(DeactivationRefusedException.Reason.SELF, assertThrows(DeactivationRefusedException.class,
                () -> admin.deactivate(carol, carol, null)).getReason());
        assertTrue(employees.findById(carol).orElseThrow().isActive());
    }

    @Test
    void theLastActiveAdministratorKeepsTheRole() {
        long olga = person("olga.admin@example.com", "Olga", "Admin", Role.ADMIN, null, true);
        jdbc.update("update employee set is_active = false where role = 'ADMIN' and id not in (?, ?)", carol, olga);
        try {
            admin.deactivate(carol, olga, null); // two administrators: one may go
            EmployeeValidationException lastAdmin = assertThrows(EmployeeValidationException.class,
                    () -> admin.update(carol, carol, new EmployeeInput("x@example.com", "Carol", "Admin", Role.MANAGER,
                            null, engineering.getId())));
            assertTrue(lastAdmin.has(Problem.LAST_ADMIN));
            assertEquals(Role.ADMIN, employees.findById(carol).orElseThrow().getRole());
        } finally {
            jdbc.update("update employee set is_active = true where role = 'ADMIN'");
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    private long person(String email, String first, String last, Role role, Long managerId, boolean active) {
        Employee employee = employees.findByEmailIgnoreCase(email).orElseGet(() -> new Employee(email, first, last,
                role, engineering.getId()));
        employee.setFirstName(first);
        employee.setLastName(last);
        employee.setRole(role);
        employee.setDepartmentId(engineering.getId());
        employee.setManagerId(managerId);
        employee.setActive(active);
        return employees.save(employee).getId();
    }

    private EmployeeRow rowOf(long id) {
        return overview.search(carol, com.stubu.specdriven.admin.EmployeeOverviewService.Query.ALL).stream()
                .filter(row -> row.id() == id).findFirst().orElseThrow();
    }

    /** Fills the open form; a {@code null} manager leaves it empty. */
    private void fill(Dialog dialog, String email, String first, String last, String role, String department,
            String manager) {
        test(textField(dialog, "employee-email")).setValue(email);
        test(textField(dialog, "employee-first-name")).setValue(first);
        test(textField(dialog, "employee-last-name")).setValue(last);
        test(select(dialog, "employee-role", Role.class)).selectItem(role);
        test(select(dialog, "employee-department", Object.class)).selectItem(department);
        if (manager != null) {
            test(select(dialog, "employee-manager", Object.class)).selectItem(manager);
        }
    }

    private EmployeeManagementView openList() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login"); // every call builds a fresh page
        UI.getCurrent().navigate(EmployeeManagementView.class);
        return find(EmployeeManagementView.class).single();
    }

    private Div rowFor(String name) {
        return find(Div.class).all().stream().filter(div -> "manage-row".equals(div.getTestId()))
                .filter(div -> text(div).contains(name)).findFirst().orElse(null);
    }

    private TextField textField(Component scope, String testId) {
        return find(TextField.class).from(scope).all().stream().filter(field -> testId.equals(field.getTestId()))
                .findFirst().orElseThrow(() -> new AssertionError("No field " + testId));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> Select<T> select(Component scope, String testId, Class<T> type) {
        return (Select<T>) find(Select.class).from(scope).all().stream()
                .filter(select -> testId.equals(select.getTestId())).findFirst()
                .orElseThrow(() -> new AssertionError("No select " + testId));
    }

    private Button button(String testId) {
        return buttons(testId).stream().findFirst().orElseThrow(() -> new AssertionError("No button " + testId));
    }

    private Button button(Component scope, String testId) {
        return buttons(scope, testId).stream().findFirst()
                .orElseThrow(() -> new AssertionError("No button " + testId));
    }

    private List<Button> buttons(Component scope, String testId) {
        return find(Button.class).from(scope).all().stream().filter(button -> testId.equals(button.getTestId()))
                .toList();
    }

    private List<Button> buttons(String testId) {
        return find(Button.class).all().stream().filter(button -> testId.equals(button.getTestId())).toList();
    }

}
