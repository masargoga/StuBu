package com.stubu.specdriven.usecases.uc013_admin_view_audit_logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.stubu.specdriven.testsupport.ViewTexts.text;

import com.stubu.specdriven.admin.AuditLogService;
import com.stubu.specdriven.admin.AuditLogService.Filter;
import com.stubu.specdriven.admin.AuditLogView;
import com.stubu.specdriven.admin.AuditSummary;
import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditLogEntry;
import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.employee.AdminOnlyException;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-013 Administrator View Audit Logs, as the administrator Carol. The log holds 60 time entry entries by Alice
 * (one per hour from 2026-09-01 08:00 UTC, oldest first), an approval by Bob with a reason, an employee change by
 * Carol and an automatic entry without user.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee(email = "carol.admin@example.com", firstName = "Carol", lastName = "Admin", role = Role.ADMIN)
class UC013AdminViewAuditLogs extends SpringBrowserlessTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    @Autowired
    MutableClock clock;
    @Autowired
    AuditService auditService;
    @Autowired
    AuditLogService service;
    @MockitoSpyBean
    AuditLogRepository auditLog;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    JdbcTemplate jdbc;

    private long carol;
    private long bob;
    private long alice;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(auditLog);
        jdbc.update("delete from audit_log");
        Department department = departments.findAll().stream().findFirst().orElseThrow();
        carol = person("carol.admin@example.com", "Carol", "Admin", Role.ADMIN, department);
        bob = person("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, department);
        alice = person("alice.employee@example.com", "Alice", "Employee", Role.EMPLOYEE, department);
        for (int hour = 0; hour < 60; hour++) {
            at("2026-09-01T08:00:00Z", hour);
            auditService.record(alice, "TimeEntry", 100L + hour, AuditAction.CREATE, null,
                    "{\"checkInAt\":\"2026-09-01T08:00:00Z\"}", "Check-in");
        }
        at("2026-09-10T09:00:00Z", 0);
        auditService.record(bob, "Timesheet", 7L, AuditAction.APPROVE, "{\"status\":\"SUBMITTED\"}",
                "{\"status\":\"APPROVED\",\"period\":\"2026-08\"}", "Looks good, thanks");
        at("2026-09-11T09:00:00Z", 0);
        auditService.record(carol, "Employee", alice, AuditAction.UPDATE, "{\"role\":\"EMPLOYEE\",\"active\":true}",
                "{\"role\":\"MANAGER\",\"active\":true}", null);
        at("2026-09-12T09:00:00Z", 0);
        auditService.record(null, "Employee", null, AuditAction.LOGIN_FAILURE, null, null, "unknown@example.com");
        at("2026-10-01T00:00:00Z", 0);
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(auditLog);
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_theNewestEntriesComeFirstAndCanBeBrowsedPageByPage() {
        AuditLogView view = openView();

        assertTrue(text(view).contains("63 entries"), text(view));
        assertTrue(text(view).contains("Page 1 of 3"), text(view));
        assertEquals(25, rows().size());
        assertTrue(text(rows().get(0)).contains("Employee") && text(rows().get(0)).contains("System")
                && text(rows().get(0)).contains("Failed login"), "The newest: the automatic entry\n" + text(rows().get(0)));
        assertTrue(text(rows().get(1)).contains("Carol Admin") && text(rows().get(1)).contains("Update"),
                text(rows().get(1)));
        assertTrue(text(rows().get(2)).contains("Bob Manager") && text(rows().get(2)).contains("Approve")
                && text(rows().get(2)).contains("Looks good, thanks"), text(rows().get(2)));
        assertFalse(button("audit-previous").isEnabled());

        test(button("audit-next")).click();
        assertTrue(text(find(AuditLogView.class).single()).contains("Page 2 of 3"));
        assertEquals(25, rows().size());
        test(button("audit-next")).click();
        assertEquals(13, rows().size(), "The rest");
        assertFalse(button("audit-next").isEnabled());
        assertTrue(text(rows().getLast()).contains("Alice Employee") && text(rows().getLast()).contains("#100"),
                "The oldest entry is last: " + text(rows().getLast()));
    }

    @Test
    void mainFlow_theColumnsShowTimeUserEntityActionReasonAndChanges() {
        openView();

        String row = text(rows().get(2)); // Bob's approval
        assertTrue(row.contains("Sep 10, 2026, 9:00:00 AM"), row);
        assertTrue(row.contains("Timesheet #7"), row);
        assertTrue(row.contains("status: SUBMITTED → APPROVED"), "Changes are summarised: " + row);
    }

    @Test
    void mainFlow_anEntryCanBeOpenedForItsFullDetails() {
        openView();

        test(button(rows().get(2), "audit-details")).click();

        Dialog dialog = find(Dialog.class).single();
        String text = text(dialog);
        assertEquals("Audit entry " + entryId("Timesheet", 7L), dialog.getHeaderTitle());
        assertTrue(text.contains("Sep 10, 2026, 9:00:00 AM"), text);
        assertTrue(text.contains("Bob Manager (bob.manager@example.com, #" + bob + ")"), text);
        assertTrue(text.contains("Timesheet #7") && text.contains("Approve") && text.contains("Looks good, thanks"),
                text);
        assertTrue(text.contains("status: SUBMITTED"), "Old values: " + text);
        assertTrue(text.contains("status: APPROVED") && text.contains("period: 2026-08"), "New values: " + text);
        test(button(dialog, "audit-close")).click();
        assertFalse(dialog.isOpened());
    }

    @Test
    void mainFlow_filtersNarrowTheResults() {
        AuditLogView view = openView();

        select(AuditAction.class, "audit-action").setValue(AuditAction.APPROVE);
        test(button("audit-search")).click();
        assertEquals(1, rows().size());
        assertTrue(text(view).contains("1 entries") || text(find(AuditLogView.class).single()).contains("1 entries"));

        select(AuditAction.class, "audit-action").clear();
        select(String.class, "audit-entity-type").setValue("Employee");
        test(button("audit-search")).click();
        assertEquals(2, rows().size(), "The employee change and the failed login");

        select(String.class, "audit-entity-type").clear();
        test(field("audit-user")).setValue("alice");
        test(button("audit-search")).click();
        assertEquals(25, rows().size(), "Alice's entries, first page");
        assertTrue(text(find(AuditLogView.class).single()).contains("60 entries"));

        test(field("audit-user")).setValue("BOB.manager@");
        test(button("audit-search")).click();
        assertEquals(1, rows().size(), "By email, in any case");

        test(field("audit-user")).setValue("system");
        test(button("audit-search")).click();
        assertEquals(1, rows().size(), "Automatic entries have no user");
        assertTrue(text(rows().getFirst()).contains("Failed login"));

        test(button("audit-reset")).click();
        assertTrue(text(find(AuditLogView.class).single()).contains("63 entries"));
    }

    @Test
    void mainFlow_theDateRangeIncludesBothDays() {
        openView();

        datePicker("audit-from").setValue(LocalDate.of(2026, 9, 10));
        datePicker("audit-to").setValue(LocalDate.of(2026, 9, 11));
        test(button("audit-search")).click();

        assertEquals(2, rows().size(), "Bob's approval on the 10th and Carol's change on the 11th");
        Filter oneDay = new Filter(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 12), null, null, null);
        assertEquals(1, service.search(carol, oneDay, UTC, 0).total());
        assertEquals(0, service.search(carol, new Filter(null, LocalDate.of(2026, 8, 31), null, null, null), UTC, 0)
                .total());
        // The day is the day in the user's time zone.
        assertEquals(1, service.search(carol, new Filter(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 12), null,
                null, null), ZoneId.of("Pacific/Auckland"), 0).total(),
                "09:00 UTC is 21:00 in Auckland, the same day");
    }

    // --- AF-1 .. AF-3 ---------------------------------------------------------------------------

    @Test
    void af1_anEmptyLogSaysSo() {
        jdbc.update("delete from audit_log");

        AuditLogView view = openView();

        // Opening the page itself is not audited, so the log stays empty.
        assertTrue(text(view).contains("No audit logs found."), text(view));
        assertTrue(rows().isEmpty());
        assertFalse(service.hasEntries(carol));
    }

    @Test
    void af2_filtersWithoutMatchesSaySo() {
        AuditLogView view = openView();

        test(field("audit-user")).setValue("nobody with this name");
        test(button("audit-search")).click();

        assertTrue(text(find(AuditLogView.class).single()).contains("No audit logs match your filters."), text(view));
        assertTrue(rows().isEmpty());
        assertFalse(text(view).contains("No audit logs found."), "The log itself is not empty");
    }

    @Test
    void af3_databaseErrorShowsAnErrorAndARetry() {
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(auditLog).count();

        AuditLogView view = openView();

        assertTrue(text(view).contains("Unable to load audit logs. Please try again."), text(view));
        assertTrue(rows().isEmpty());

        Mockito.reset(auditLog);
        test(button("retry")).click();

        assertFalse(text(view).contains("Unable to load audit logs"), text(view));
        assertEquals(25, rows().size());
    }

    // --- AF-4: Export ---------------------------------------------------------------------------

    @Test
    void af4_theMatchingEntriesCanBeExportedAsCsv() {
        auditService.record(alice, "Employee", 5L, AuditAction.UPDATE, null, null, "=HYPERLINK(\"http://evil\")");
        auditService.record(alice, "Employee", 6L, AuditAction.UPDATE, null, "{\"name\":\"O\\\"Neil, Pat\"}", "line1\nline2");

        String csv = service.exportCsv(carol, new Filter(null, null, "alice", "Employee", null), UTC);

        assertTrue(csv.startsWith("﻿"), "A byte order mark, so spreadsheets read UTF-8");
        String[] lines = csv.substring(1).split("\r\n");
        assertEquals("timestamp,user,user email,entity type,entity id,action,reason,old values,new values", lines[0]);
        assertTrue(lines[1].contains("\"Alice Employee\"") && lines[1].contains("\"alice.employee@example.com\""),
                lines[1]);
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""), "A formula is turned into text: " + csv);
        assertTrue(csv.contains("\"O\\\"Neil, Pat\"") || csv.contains("O\\\"\"Neil"), "Quotes are doubled: " + csv);
        assertFalse(csv.contains("\"TimeEntry\""), "The filter applies to the export");
    }

    @Test
    void af4_theExportLinkIsOnlyOfferedWhenThereIsSomethingToExport() {
        AuditLogView view = openView();
        assertTrue(find(Anchor.class).all().stream().anyMatch(a -> "audit-export".equals(a.getTestId())));

        test(field("audit-user")).setValue("nobody with this name");
        test(button("audit-search")).click();

        assertTrue(find(Anchor.class).all().stream().noneMatch(a -> "audit-export".equals(a.getTestId())),
                text(view));
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_onlyActiveAdministratorsMayReadTheLog() {
        for (long notAdmin : List.of(bob, alice)) {
            assertThrows(AdminOnlyException.class, () -> service.search(notAdmin, Filter.NONE, UTC, 0));
            assertThrows(AdminOnlyException.class, () -> service.exportCsv(notAdmin, Filter.NONE, UTC));
            assertThrows(AdminOnlyException.class, () -> service.hasEntries(notAdmin));
            assertThrows(AdminOnlyException.class, () -> service.entityTypes(notAdmin));
        }
        jdbc.update("update employee set is_active = false where id = ?", carol);
        try {
            assertThrows(AdminOnlyException.class, () -> service.search(carol, Filter.NONE, UTC, 0));
        } finally {
            jdbc.update("update employee set is_active = true where id = ?", carol);
        }
    }

    @Test
    void br03_theLogCannotBeChangedOrDeletedThroughTheApplication() {
        for (Method method : AuditLogRepository.class.getMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            assertFalse(name.startsWith("delete") || name.startsWith("remove") || name.startsWith("update"),
                    "No way to delete or update: " + method);
        }
        for (Method method : AuditLogEntry.class.getMethods()) {
            assertFalse(method.getName().startsWith("set") && Modifier.isPublic(method.getModifiers()),
                    "An entry has no setters: " + method);
        }
        openView();
        assertTrue(find(Button.class).all().stream().noneMatch(button -> Arrays.asList("edit", "delete")
                .contains(String.valueOf(button.getTestId()))), "The page offers no editing");
    }

    @Test
    void br04_everyEntryHasTimeUserEntityActionAndOldAndNewValues() {
        AuditLogService.Row row = service.search(carol, new Filter(null, null, null, "Employee", AuditAction.UPDATE),
                UTC, 0).rows().getFirst();

        assertEquals(Instant.parse("2026-09-11T09:00:00Z"), row.timestamp());
        assertEquals("Carol Admin", row.userName());
        assertEquals("carol.admin@example.com", row.userEmail());
        assertEquals("Employee", row.entityType());
        assertEquals(alice, row.entityId());
        assertEquals("{\"role\":\"EMPLOYEE\",\"active\":true}", row.oldValues());
        assertEquals("{\"role\":\"MANAGER\",\"active\":true}", row.newValues());
        assertEquals("role: EMPLOYEE → MANAGER", row.summary());
    }

    @Test
    void br06_automaticEntriesShowAsSystem() {
        AuditLogService.Row row = service.search(carol, new Filter(null, null, "system", null, null), UTC, 0).rows()
                .getFirst();

        assertTrue(row.isSystem());
        assertEquals("unknown@example.com", row.reason());
    }

    @Test
    void theSummaryReadsTheStoredJson() {
        assertEquals("role: EMPLOYEE → MANAGER", AuditSummary.of("{\"role\":\"EMPLOYEE\",\"a\":1}",
                "{\"role\":\"MANAGER\",\"a\":1}"));
        assertEquals("email: a@b.c, active: true", AuditSummary.of(null, "{\"email\":\"a@b.c\",\"active\":true}"));
        assertEquals("not json", AuditSummary.of(null, "not json"));
        assertEquals("", AuditSummary.of(null, null));
        assertEquals("a: he said \"hi\"\nb: x", AuditSummary.lines("{\"a\":\"he said \\\"hi\\\"\",\"b\":\"x\"}"));
        assertEquals("{\"nested\":{\"a\":1}}", AuditSummary.lines("{\"nested\":{\"a\":1}}"));
        assertTrue(AuditSummary.of(null, "{\"k\":\"" + "x".repeat(300) + "\"}").length() <= 140);
    }

    // --- helpers --------------------------------------------------------------------------------

    private void at(String start, int hoursLater) {
        clock.set(Instant.parse(start).plusSeconds(3600L * hoursLater));
    }

    private long entryId(String entityType, Long entityId) {
        return jdbc.queryForObject("select id from audit_log where entity_type = ? and entity_id = ?", Long.class,
                entityType, entityId);
    }

    private long person(String email, String first, String last, Role role, Department department) {
        Employee employee = employees.findByEmailIgnoreCase(email)
                .orElseGet(() -> new Employee(email, first, last, role, department.getId()));
        employee.setRole(role);
        employee.setActive(true);
        return employees.save(employee).getId();
    }

    private AuditLogView openView() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login"); // every call builds a fresh page
        UI.getCurrent().navigate(AuditLogView.class);
        return find(AuditLogView.class).single();
    }

    private List<Div> rows() {
        return find(Div.class).all().stream().filter(div -> "audit-row".equals(div.getTestId())).toList();
    }

    private TextField field(String testId) {
        return find(TextField.class).all().stream().filter(field -> testId.equals(field.getTestId())).findFirst()
                .orElseThrow(() -> new AssertionError("No field " + testId));
    }

    private DatePicker datePicker(String testId) {
        return find(DatePicker.class).all().stream().filter(picker -> testId.equals(picker.getTestId())).findFirst()
                .orElseThrow(() -> new AssertionError("No date picker " + testId));
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
