package com.stubu.specdriven.usecases.uc012_admin_configure_public_holidays;

import com.stubu.specdriven.region.Region;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static com.stubu.specdriven.testsupport.ViewTexts.text;

import com.stubu.specdriven.admin.PublicHolidayView;
import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditLogEntry;
import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.employee.AdminOnlyException;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.EditConflictException;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.holiday.HolidayValidationException;
import com.stubu.specdriven.holiday.HolidayValidationException.Problem;
import com.stubu.specdriven.holiday.PublicHoliday;
import com.stubu.specdriven.holiday.PublicHolidayRepository;
import com.stubu.specdriven.holiday.PublicHolidayService;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetService;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** UC-012 Administrator Configure Public Holidays, as the administrator Carol. It is 2026-10-05. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee(email = "carol.admin@example.com", firstName = "Carol", lastName = "Admin", role = Role.ADMIN)
class UC012AdminConfigurePublicHolidays extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

    @Autowired
    MutableClock clock;
    @Autowired
    PublicHolidayService service;
    @MockitoSpyBean
    PublicHolidayRepository holidays;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    MonthlyTimesheetService monthly;
    @Autowired
    JdbcTemplate jdbc;

    private long carol;
    private long alice;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(holidays);
        clock.set(NOW);
        jdbc.update("delete from public_holiday");
        Department department = departments.findAll().stream().findFirst().orElseThrow();
        carol = person("carol.admin@example.com", "Carol", "Admin", Role.ADMIN, department);
        alice = person("alice.employee@example.com", "Alice", "Employee", Role.EMPLOYEE, department);
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(holidays);
    }

    // --- Main Flow: Add -------------------------------------------------------------------------

    @Test
    void mainFlow_add_createsTheHolidayAndAuditsIt() {
        PublicHolidayView view = openList();
        assertTrue(text(view).contains("No public holidays are configured for Default in 2026."), text(view));
        test(button("add-holiday")).click();
        Dialog dialog = find(Dialog.class).single();
        int audits = auditLog.findAllByOrderByIdAsc().size();
        fill(dialog, LocalDate.of(2026, 12, 25), "Christmas");

        test(button(dialog, "holiday-save")).click();

        assertFalse(dialog.isOpened());
        PublicHoliday saved = holidays.findByRegionIdAndDate(Region.DEFAULT_ID, LocalDate.of(2026, 12, 25)).orElseThrow();
        assertEquals("Christmas", saved.getName());
        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.CREATE, entry.getAction());
        assertEquals(carol, entry.getUserId());
        assertEquals("PublicHoliday", entry.getEntityType());
        assertEquals(saved.getId(), entry.getEntityId());
        assertTrue(entry.getNewValues().contains("\"date\":\"2026-12-25\"") && entry.getNewValues()
                .contains("\"name\":\"Christmas\""), entry.getNewValues());
        assertTrue(text(view).contains("Holiday Christmas added on Dec 25, 2026."), text(view));
        assertEquals(1, holidayRows().size(), "Back in the list");
        assertTrue(text(holidayRows().getFirst()).contains("Friday, December 25, 2026"), text(holidayRows().getFirst()));
    }

    // --- Main Flow: Edit ------------------------------------------------------------------------

    @Test
    void mainFlow_edit_changesNameAndDateAndAuditsOldAndNewValues() {
        PublicHoliday holiday = holidays.save(new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas"));
        openList();
        test(button(holidayRows().getFirst(), "edit-holiday")).click();
        Dialog dialog = find(Dialog.class).single();
        assertEquals(LocalDate.of(2026, 12, 25), datePicker(dialog).getValue(), "Prefilled");
        assertEquals("Christmas", textField(dialog, "holiday-name").getValue());
        int audits = auditLog.findAllByOrderByIdAsc().size();
        fill(dialog, LocalDate.of(2026, 12, 26), "Boxing Day");

        test(button(dialog, "holiday-save")).click();

        assertFalse(dialog.isOpened());
        PublicHoliday updated = holidays.findById(holiday.getId()).orElseThrow();
        assertEquals(LocalDate.of(2026, 12, 26), updated.getDate());
        assertEquals("Boxing Day", updated.getName());
        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.UPDATE, entry.getAction());
        assertTrue(entry.getOldValues().contains("\"name\":\"Christmas\"") && entry.getOldValues()
                .contains("2026-12-25"), entry.getOldValues());
        assertTrue(entry.getNewValues().contains("\"name\":\"Boxing Day\"") && entry.getNewValues()
                .contains("2026-12-26"), entry.getNewValues());
        assertTrue(text(find(PublicHolidayView.class).single()).contains("Holiday updated."));
    }

    @Test
    void mainFlow_edit_keepingTheDateIsNotADuplicateAndWithoutChangesNothingIsAudited() {
        PublicHoliday holiday = holidays.save(new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas"));
        int audits = auditLog.findAllByOrderByIdAsc().size();

        service.update(carol, holiday.getId(), LocalDate.of(2026, 12, 25), "Christmas");

        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        service.update(carol, holiday.getId(), LocalDate.of(2026, 12, 25), "Christmas Day");
        assertEquals(audits + 1, auditLog.findAllByOrderByIdAsc().size());
    }

    // --- Main Flow: Delete ----------------------------------------------------------------------

    @Test
    void mainFlow_delete_removesTheHolidayAfterConfirmationAndAuditsIt() {
        PublicHoliday holiday = holidays.save(new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas"));
        openList();
        test(button(holidayRows().getFirst(), "delete-holiday")).click();
        Dialog dialog = find(Dialog.class).single();
        assertTrue(text(dialog).contains("Delete Christmas on Dec 25, 2026? This cannot be undone."), text(dialog));
        assertTrue(holidays.findById(holiday.getId()).isPresent(), "Nothing happens before the confirmation");
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button(dialog, "delete-confirm")).click();

        assertFalse(dialog.isOpened());
        assertTrue(holidays.findById(holiday.getId()).isEmpty());
        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc();
        assertEquals(audits + 1, audit.size());
        AuditLogEntry entry = audit.getLast();
        assertEquals(AuditAction.DELETE, entry.getAction());
        assertEquals(holiday.getId(), entry.getEntityId());
        assertTrue(entry.getOldValues().contains("Christmas") && entry.getOldValues().contains("2026-12-25"),
                entry.getOldValues());
        assertTrue(text(find(PublicHolidayView.class).single()).contains("Holiday deleted."));
        assertTrue(holidayRows().isEmpty());
    }

    // --- AF-1 .. AF-3: Validation ---------------------------------------------------------------

    @Test
    void af1_aDateThatHasAHolidayAlreadyIsRefused() {
        holidays.save(new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas"));
        openList();
        test(button("add-holiday")).click();
        Dialog dialog = find(Dialog.class).single();
        fill(dialog, LocalDate.of(2026, 12, 25), "Another");

        test(button(dialog, "holiday-save")).click();

        assertTrue(dialog.isOpened(), "The form stays open");
        assertEquals("A holiday is already configured for Dec 25, 2026. Please select a different date or edit the "
                + "existing holiday.", datePicker(dialog).getErrorMessage());
        assertEquals(1, holidays.count());

        datePicker(dialog).setValue(LocalDate.of(2026, 12, 24)); // corrected and resubmitted
        test(button(dialog, "holiday-save")).click();
        assertFalse(dialog.isOpened());
        assertEquals(2, holidays.count());
    }

    @Test
    void af1_editingToAnotherHolidaysDateIsRefused() {
        holidays.save(new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas"));
        PublicHoliday other = holidays.save(new PublicHoliday(LocalDate.of(2026, 12, 26), "Boxing Day"));

        HolidayValidationException taken = assertThrows(HolidayValidationException.class,
                () -> service.update(carol, other.getId(), LocalDate.of(2026, 12, 25), "Boxing Day"));

        assertTrue(taken.has(Problem.DATE_TAKEN));
        assertEquals(LocalDate.of(2026, 12, 26), holidays.findById(other.getId()).orElseThrow().getDate());
    }

    @Test
    void af2_anInvalidOrMissingDateIsRefused() {
        openList();
        test(button("add-holiday")).click();
        Dialog dialog = find(Dialog.class).single();
        test(textField(dialog, "holiday-name")).setValue("Christmas");

        test(button(dialog, "holiday-save")).click(); // no date

        assertTrue(dialog.isOpened());
        assertEquals("Please enter a valid date.", datePicker(dialog).getErrorMessage());
        assertTrue(datePicker(dialog).isInvalid());
        for (LocalDate date : java.util.Arrays.asList(null, LocalDate.of(1999, 12, 31), LocalDate.of(2101, 1, 1))) {
            assertTrue(assertThrows(HolidayValidationException.class, () -> service.add(carol, Region.DEFAULT_ID, date, "Christmas"))
                    .has(Problem.DATE_INVALID), String.valueOf(date));
        }
        assertEquals(0, holidays.count());
    }

    @Test
    void af3_anEmptyNameIsRefused() {
        openList();
        test(button("add-holiday")).click();
        Dialog dialog = find(Dialog.class).single();
        datePicker(dialog).setValue(LocalDate.of(2026, 12, 25));
        test(textField(dialog, "holiday-name")).setValue("   ");

        test(button(dialog, "holiday-save")).click();

        assertTrue(dialog.isOpened());
        assertEquals("Please enter a holiday name.", textField(dialog, "holiday-name").getErrorMessage());
        assertEquals(0, holidays.count());
        assertTrue(assertThrows(HolidayValidationException.class, () -> service.add(carol, Region.DEFAULT_ID, LocalDate.of(2026, 12, 25),
                null)).has(Problem.NAME_REQUIRED));
    }

    // --- AF-4: Database error ------------------------------------------------------------------

    @Test
    void af4_databaseErrorWhileAddingKeepsTheFormOpenForARetry() {
        openList();
        test(button("add-holiday")).click();
        Dialog dialog = find(Dialog.class).single();
        fill(dialog, LocalDate.of(2026, 12, 25), "Christmas");
        int audits = auditLog.findAllByOrderByIdAsc().size();
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(holidays)
                .saveAndFlush(any(PublicHoliday.class));

        test(button(dialog, "holiday-save")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Unable to save changes. Please try again."), text(dialog));
        assertEquals(0, holidays.count());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());

        Mockito.reset(holidays);
        test(button(dialog, "holiday-save")).click(); // retry
        assertFalse(dialog.isOpened());
        assertEquals(1, holidays.count());
    }

    @Test
    void af4_databaseErrorWhileEditingAndDeletingChangesNothing() {
        PublicHoliday holiday = holidays.save(new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas"));
        openList();
        test(button(holidayRows().getFirst(), "edit-holiday")).click();
        Dialog dialog = find(Dialog.class).single();
        test(textField(dialog, "holiday-name")).setValue("Xmas");
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(holidays)
                .saveAndFlush(any(PublicHoliday.class));

        test(button(dialog, "holiday-save")).click();

        assertTrue(dialog.isOpened());
        assertTrue(text(dialog).contains("Unable to save changes. Please try again."), text(dialog));
        assertEquals("Christmas", holidays.findById(holiday.getId()).orElseThrow().getName());
        test(button(dialog, "holiday-cancel")).click();

        Mockito.reset(holidays);
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(holidays)
                .delete(any(PublicHoliday.class));
        test(button(holidayRows().getFirst(), "delete-holiday")).click();
        Dialog confirm = find(Dialog.class).single();
        test(button(confirm, "delete-confirm")).click();

        assertTrue(confirm.isOpened(), "A retry is offered");
        assertTrue(text(confirm).contains("Unable to save changes. Please try again."), text(confirm));
        assertTrue(holidays.findById(holiday.getId()).isPresent());
        Mockito.reset(holidays);
        test(button(confirm, "delete-confirm")).click();
        assertTrue(holidays.findById(holiday.getId()).isEmpty());
    }

    // --- AF-5: Cancel ---------------------------------------------------------------------------

    @Test
    void af5_cancellingChangesNothing() {
        PublicHoliday holiday = holidays.save(new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas"));
        openList();
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button("add-holiday")).click();
        Dialog form = find(Dialog.class).single();
        fill(form, LocalDate.of(2026, 12, 31), "New Year's Eve");
        test(button(form, "holiday-cancel")).click();
        assertFalse(form.isOpened());

        test(button(holidayRows().getFirst(), "delete-holiday")).click();
        Dialog confirm = find(Dialog.class).single();
        test(button(confirm, "delete-cancel")).click();
        assertFalse(confirm.isOpened());

        assertEquals(1, holidays.count());
        assertEquals("Christmas", holidays.findById(holiday.getId()).orElseThrow().getName());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_onlyActiveAdministratorsMayManageHolidays() {
        long bob = person("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, departments.findAll().getFirst());
        for (long notAdmin : List.of(bob, alice)) {
            assertThrows(AdminOnlyException.class, () -> service.add(notAdmin, Region.DEFAULT_ID, LocalDate.of(2026, 12, 25), "Christmas"));
            assertThrows(AdminOnlyException.class, () -> service.list(notAdmin));
        }
        jdbc.update("update employee set is_active = false where id = ?", carol);
        try {
            assertThrows(AdminOnlyException.class, () -> service.add(carol, Region.DEFAULT_ID, LocalDate.of(2026, 12, 25), "Christmas"));
        } finally {
            jdbc.update("update employee set is_active = true where id = ?", carol);
        }
        assertEquals(0, holidays.count());
    }

    @Test
    void br02_eachDateHasAtMostOneHolidayEvenWhenTwoAdministratorsAddItAtOnce() {
        service.add(carol, Region.DEFAULT_ID, LocalDate.of(2026, 12, 25), "Christmas");
        assertTrue(assertThrows(HolidayValidationException.class, () -> service.add(carol, Region.DEFAULT_ID, LocalDate.of(2026, 12, 25),
                "Christmas again")).has(Problem.DATE_TAKEN));
        // The database enforces it too: a second row for the same date cannot exist.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> holidays.saveAndFlush(new PublicHoliday(LocalDate.of(2026, 12, 25), "Duplicate")));
        assertEquals(1, holidays.count());
    }

    @Test
    void br07_aSaveBasedOnAnOutdatedVersionIsRefusedInsteadOfOverwritingTheOtherChange() {
        PublicHoliday opened = service.add(carol, Region.DEFAULT_ID, LocalDate.of(2026, 12, 25), "Christmas");
        Long openedVersion = opened.getVersion();
        // Another administrator renames the holiday after the form was opened.
        service.update(carol, opened.getId(), LocalDate.of(2026, 12, 25), "Christmas Day", openedVersion);
        int audits = auditLog.findAllByOrderByIdAsc().size();

        assertThrows(EditConflictException.class, () -> service.update(carol, opened.getId(),
                LocalDate.of(2026, 12, 26), "Boxing Day", openedVersion));

        PublicHoliday stored = holidays.findById(opened.getId()).orElseThrow();
        assertEquals("Christmas Day", stored.getName(), "The other change is still there");
        assertEquals(LocalDate.of(2026, 12, 25), stored.getDate());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size(), "A refused save is not audited");
        service.update(carol, opened.getId(), LocalDate.of(2026, 12, 26), "Boxing Day", stored.getVersion());
        assertEquals("Boxing Day", holidays.findById(opened.getId()).orElseThrow().getName());
    }

    @Test
    void br08_creationTimesComeFromTheApplicationClock() {
        PublicHoliday added = service.add(carol, Region.DEFAULT_ID, LocalDate.of(2026, 12, 25), "Christmas");

        java.sql.Timestamp created = jdbc.queryForObject("select created_at from public_holiday where id = ?",
                java.sql.Timestamp.class, added.getId());
        assertEquals(NOW, created.toInstant(), "Not the wall clock");
    }

    @Test
    void br04_pastAndFutureDatesAreAllowed() {
        service.add(carol, Region.DEFAULT_ID, LocalDate.of(2020, 1, 1), "Past");
        service.add(carol, Region.DEFAULT_ID, LocalDate.of(2026, 10, 5), "Today");
        service.add(carol, Region.DEFAULT_ID, LocalDate.of(2030, 5, 1), "Future");

        assertEquals(3, holidays.count());
    }

    @Test
    void br06_holidaysAppearInTheMonthViewButNeverChangeTheHours() {
        var before = monthly.load(alice, YearMonth.of(2026, 9), ZoneOffset.UTC);

        service.add(carol, Region.DEFAULT_ID, LocalDate.of(2026, 9, 15), "Founders Day");

        var after = monthly.load(alice, YearMonth.of(2026, 9), ZoneOffset.UTC);
        assertEquals("Founders Day", after.days().get(14).holidayName());
        assertNull(before.days().get(14).holidayName());
        assertEquals(before.totalWorked(), after.totalWorked());
    }

    @Test
    void theYearFilterShowsTheCurrentYearFirstAndCanShowAllYears() {
        holidays.save(new PublicHoliday(LocalDate.of(2025, 12, 25), "Christmas 2025"));
        holidays.save(new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas 2026"));
        PublicHolidayView view = openList();

        assertEquals(1, holidayRows().size());
        assertTrue(text(view).contains("Christmas 2026") && !text(view).contains("Christmas 2025"), text(view));

        yearSelect().setValue(null);

        assertEquals(2, holidayRows().size());
        assertTrue(text(holidayRows().getFirst()).contains("Christmas 2025"), "Earliest date first");
    }

    // --- helpers --------------------------------------------------------------------------------

    private long person(String email, String first, String last, Role role, Department department) {
        Employee employee = employees.findByEmailIgnoreCase(email)
                .orElseGet(() -> new Employee(email, first, last, role, department.getId()));
        employee.setRole(role);
        employee.setActive(true);
        return employees.save(employee).getId();
    }

    private void fill(Dialog dialog, LocalDate date, String name) {
        datePicker(dialog).setValue(date);
        test(textField(dialog, "holiday-name")).setValue(name);
    }

    private PublicHolidayView openList() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login"); // every call builds a fresh page
        UI.getCurrent().navigate(PublicHolidayView.class);
        return find(PublicHolidayView.class).single();
    }

    private List<Div> holidayRows() {
        return find(Div.class).all().stream().filter(div -> "holiday-row".equals(div.getTestId())).toList();
    }

    private DatePicker datePicker(Component scope) {
        return find(DatePicker.class).from(scope).single();
    }

    @SuppressWarnings("unchecked")
    private Select<Integer> yearSelect() {
        return find(Select.class).all().stream().filter(select -> "holiday-year".equals(select.getTestId()))
                .findFirst().orElseThrow();
    }

    private TextField textField(Component scope, String testId) {
        return find(TextField.class).from(scope).all().stream().filter(field -> testId.equals(field.getTestId()))
                .findFirst().orElseThrow(() -> new AssertionError("No field " + testId));
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
