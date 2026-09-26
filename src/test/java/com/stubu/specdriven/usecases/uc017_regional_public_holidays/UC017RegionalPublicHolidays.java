package com.stubu.specdriven.usecases.uc017_regional_public_holidays;

import static com.stubu.specdriven.testsupport.ViewTexts.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.stubu.specdriven.admin.EmployeeManagementView;
import com.stubu.specdriven.admin.PublicHolidayView;
import com.stubu.specdriven.approval.EmployeeTimesheetView;
import com.stubu.specdriven.approval.TimesheetReviewView;
import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditLogEntry;
import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.employee.AdminOnlyException;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.EditConflictException;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeAdminService;
import com.stubu.specdriven.employee.EmployeeInput;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.EmployeeValidationException;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.holiday.HolidayValidationException;
import com.stubu.specdriven.holiday.PublicHoliday;
import com.stubu.specdriven.holiday.PublicHolidayRepository;
import com.stubu.specdriven.holiday.PublicHolidayService;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheet;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetService;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetView;
import com.stubu.specdriven.region.Region;
import com.stubu.specdriven.region.RegionInUseException;
import com.stubu.specdriven.region.RegionInUseException.Reason;
import com.stubu.specdriven.region.RegionNotFoundException;
import com.stubu.specdriven.region.RegionRepository;
import com.stubu.specdriven.region.RegionRow;
import com.stubu.specdriven.region.RegionService;
import com.stubu.specdriven.region.RegionValidationException;
import com.stubu.specdriven.region.RegionValidationException.Problem;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.QueryParameters;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-017 Regional Public Holidays, as the administrator Carol unless a test says otherwise. It is 2026-10-05. Bob is a
 * manager in the region "Germany – Bavaria" and manages Dana, who works in "USA"; Alice works in "Default".
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee(email = "carol.admin@example.com", firstName = "Carol", lastName = "Admin", role = Role.ADMIN)
class UC017RegionalPublicHolidays extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final String BAVARIA = "Germany – Bavaria";
    private static final String USA = "USA";

    @Autowired
    MutableClock clock;
    @Autowired
    RegionService regionService;
    @Autowired
    PublicHolidayService holidayService;
    @Autowired
    EmployeeAdminService employeeAdmin;
    @Autowired
    MonthlyTimesheetService monthly;
    @Autowired
    TimesheetService timesheets;
    @MockitoSpyBean
    RegionRepository regions;
    @Autowired
    PublicHolidayRepository holidays;
    @Autowired
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
    private long dana;
    private long bavaria;
    private long usa;
    private Department department;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(regions);
        clock.set(NOW);
        resetRegions();
        department = departments.findAll().stream().findFirst().orElseThrow();
        bavaria = regions.save(new Region(BAVARIA)).getId();
        usa = regions.save(new Region(USA)).getId();
        carol = person("carol.admin@example.com", "Carol", "Admin", Role.ADMIN, Region.DEFAULT_ID, null);
        bob = person("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, bavaria, carol);
        alice = person("alice.employee@example.com", "Alice", "Employee", Role.EMPLOYEE, Region.DEFAULT_ID, carol);
        dana = person("dana.usa@example.com", "Dana", "American", Role.EMPLOYEE, usa, bob);
        jdbc.update("update employee set manager_id = ? where manager_id = ? and id <> ?", carol, bob, dana);
        holidays.save(new PublicHoliday(Region.DEFAULT_ID, LocalDate.of(2026, 9, 15), "Founders Day"));
        holidays.save(new PublicHoliday(usa, LocalDate.of(2026, 9, 7), "Labor Day"));
        holidays.save(new PublicHoliday(bavaria, LocalDate.of(2026, 9, 20), "Bavarian Day"));
        jdbc.update("delete from time_entry");
        jdbc.update("delete from timesheet");
    }

    @AfterEach
    void cleanUp() {
        Mockito.reset(regions);
        resetRegions();
    }

    /** Leaves only the region "Default" with its own name, and every employee in it. */
    private void resetRegions() {
        jdbc.update("delete from public_holiday");
        jdbc.update("update employee set region_id = ?", Region.DEFAULT_ID);
        jdbc.update("delete from region where id <> ?", Region.DEFAULT_ID);
        jdbc.update("update region set name = 'Default' where id = ?", Region.DEFAULT_ID);
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_theAdministratorAddsRegionsAndHolidaysAndSeesHowManyEachHas() {
        Region spain = regionService.add(carol, "  Spain  ");
        holidayService.add(carol, spain.getId(), LocalDate.of(2026, 12, 6), "Constitution Day");

        assertEquals("Spain", spain.getName(), "The name is stored trimmed");
        List<RegionRow> rows = regionService.list(carol);
        assertEquals(List.of("Default", BAVARIA, "Spain", USA), rows.stream().map(RegionRow::name).toList(),
                "Regions by name");
        RegionRow spainRow = row(rows, "Spain");
        assertEquals(0, spainRow.employees());
        assertEquals(1, spainRow.holidays());
        assertEquals(1, row(rows, USA).employees(), "Dana");
        assertEquals(employees.count() - 2, row(rows, "Default").employees(), "Everybody except Bob and Dana");
        assertEquals(Region.DEFAULT_ID, row(rows, "Default").id());
        assertEquals("Constitution Day", holidays.findByRegionIdAndDate(spain.getId(), LocalDate.of(2026, 12, 6))
                .orElseThrow().getName());
    }

    @Test
    void mainFlow_regionsAndHolidaysAreAudited() {
        int before = auditLog.findAllByOrderByIdAsc().size();

        Region spain = regionService.add(carol, "Spain");
        regionService.rename(carol, spain.getId(), "Spain (mainland)", spain.getVersion());
        PublicHoliday holiday = holidayService.add(carol, spain.getId(), LocalDate.of(2026, 12, 6), "Constitution Day");
        holidayService.delete(carol, holiday.getId());
        holidayService.delete(carol, holidays.findByRegionIdAndDate(usa, LocalDate.of(2026, 9, 7)).orElseThrow().getId());
        holidayService.delete(carol, holidays.findByRegionIdAndDate(Region.DEFAULT_ID, LocalDate.of(2026, 9, 15))
                .orElseThrow().getId());
        regionService.delete(carol, spain.getId());

        List<AuditLogEntry> audit = auditLog.findAllByOrderByIdAsc().subList(before, auditLog.findAllByOrderByIdAsc().size());
        assertEquals(7, audit.size());
        AuditLogEntry created = audit.get(0);
        assertEquals("Region", created.getEntityType());
        assertEquals(AuditAction.CREATE, created.getAction());
        assertEquals(carol, created.getUserId());
        assertTrue(created.getNewValues().contains("Spain"), created.getNewValues());
        AuditLogEntry renamed = audit.get(1);
        assertEquals(AuditAction.UPDATE, renamed.getAction());
        assertTrue(renamed.getOldValues().contains("\"Spain\"") && renamed.getNewValues().contains("Spain (mainland)"),
                renamed.getOldValues() + " -> " + renamed.getNewValues());
        AuditLogEntry holidayCreated = audit.get(2);
        assertEquals("PublicHoliday", holidayCreated.getEntityType());
        assertTrue(holidayCreated.getNewValues().contains("Spain (mainland)") && holidayCreated.getNewValues()
                .contains("Constitution Day"), "The region is part of the audited values: " + holidayCreated.getNewValues());
        AuditLogEntry deleted = audit.get(6);
        assertEquals("Region", deleted.getEntityType());
        assertEquals(AuditAction.DELETE, deleted.getAction());
        assertTrue(deleted.getOldValues().contains("Spain (mainland)"), deleted.getOldValues());
        assertNull(deleted.getNewValues());
    }

    @Test
    void mainFlow_thePageListsTheHolidaysOfTheChosenRegionOnly() {
        PublicHolidayView view = openHolidays();

        assertEquals(List.of("Default", BAVARIA, USA), regionNames(), "Every region can be chosen");
        assertEquals("Default", regionSelect().getValue().name(), "The first region by name is chosen");
        assertEquals(1, holidayRows().size());
        assertTrue(text(view).contains("Founders Day") && !text(view).contains("Labor Day"), text(view));

        choose(USA);

        assertEquals(1, holidayRows().size());
        assertTrue(text(view).contains("Labor Day") && !text(view).contains("Founders Day"), text(view));

        choose(BAVARIA);
        assertTrue(text(view).contains("Bavarian Day") && !text(view).contains("Labor Day"), text(view));
    }

    @Test
    void mainFlow_aRegionWithoutHolidaysSaysSoAndNamesTheRegion() {
        regionService.add(carol, "Spain");
        PublicHolidayView view = openHolidays();

        choose("Spain");

        assertTrue(holidayRows().isEmpty());
        assertTrue(text(view).contains("No public holidays are configured for Spain in 2026."), text(view));
        yearSelect().setValue(null);
        assertTrue(text(view).contains("No public holidays are configured for Spain yet."), text(view));
    }

    @Test
    void mainFlow_addingAHolidayOnThePageStoresItInTheChosenRegion() {
        PublicHolidayView view = openHolidays();
        choose(USA);

        test(button("add-holiday")).click();
        Dialog dialog = find(Dialog.class).single();
        assertEquals(USA, textField(dialog, "holiday-region-shown").getValue(), "The region is shown");
        assertTrue(textField(dialog, "holiday-region-shown").isReadOnly(), "and cannot be changed");
        find(DatePicker.class).from(dialog).single().setValue(LocalDate.of(2026, 11, 26));
        test(textField(dialog, "holiday-name")).setValue("Thanksgiving");
        test(button(dialog, "holiday-save")).click();

        assertFalse(dialog.isOpened());
        PublicHoliday saved = holidays.findByRegionIdAndDate(usa, LocalDate.of(2026, 11, 26)).orElseThrow();
        assertEquals("Thanksgiving", saved.getName());
        assertTrue(holidays.findByRegionIdAndDate(Region.DEFAULT_ID, LocalDate.of(2026, 11, 26)).isEmpty());
        assertEquals(USA, regionSelect().getValue().name(), "The page stays with the region");
        assertTrue(text(view).contains("Thanksgiving"), text(view));
    }

    @Test
    void mainFlow_theManageRegionsDialogAddsRenamesAndDeletesRegions() {
        PublicHolidayView view = openHolidays();
        test(button("manage-regions")).click();
        Dialog dialog = find(Dialog.class).single();
        assertEquals(3, regionRows(dialog).size());
        assertTrue(text(dialog).contains("Employees: 1, holidays: 1"), text(dialog));

        test(textField(dialog, "region-name-new")).setValue("Spain");
        test(button(dialog, "region-add")).click();

        assertTrue(text(dialog).contains("Region Spain added."), text(dialog));
        assertEquals(4, regionRows(dialog).size());
        assertTrue(regionNames().contains("Spain"), "The selector of the page is up to date: " + regionNames());
        assertEquals("", textField(dialog, "region-name-new").getValue(), "The field is ready for the next region");

        test(button(regionRow(dialog, "Spain"), "region-rename")).click();
        Dialog rename = dialogWith("region-name");
        test(textField(rename, "region-name")).setValue("Spain (mainland)");
        test(button(rename, "region-rename-save")).click();

        assertFalse(rename.isOpened());
        assertTrue(text(dialog).contains("Region renamed."), text(dialog));
        assertEquals("Spain (mainland)", regions.findByNameIgnoreCase("Spain (mainland)").orElseThrow().getName());

        test(button(regionRow(dialog, "Spain (mainland)"), "region-delete")).click();
        assertTrue(text(find(Dialog.class).all().getLast()).contains("Delete the region Spain (mainland)?"),
                text(find(Dialog.class).all().getLast()));
        test(button("region-delete-confirm")).click();

        assertTrue(text(dialog).contains("Region deleted."), text(dialog));
        assertTrue(regions.findByNameIgnoreCase("Spain (mainland)").isEmpty());
        assertEquals(3, regionRows(dialog).size());
        assertFalse(regionNames().contains("Spain (mainland)"));
        test(button(dialog, "region-close")).click();
        assertFalse(dialog.isOpened());
        assertNotNull(view);
    }

    @Test
    void mainFlow_theEmployeeFormOffersTheRegionsAndStoresTheChoice() {
        openEmployees();
        test(button("add-employee")).click();
        Dialog dialog = find(Dialog.class).single();
        Select<Object> region = select(dialog, "employee-region");
        assertEquals("Region", region.getLabel());
        assertEquals("Default", ((com.stubu.specdriven.employee.Choice) region.getValue()).label(),
                "A new employee starts with the first region");
        assertEquals(List.of("Default", BAVARIA, USA), region.getListDataView().getItems()
                .map(item -> ((com.stubu.specdriven.employee.Choice) item).label()).toList());

        test(textField(dialog, "employee-email")).setValue("zoe.zimmer@example.com");
        test(textField(dialog, "employee-first-name")).setValue("Zoe");
        test(textField(dialog, "employee-last-name")).setValue("Zimmer");
        test(select(dialog, "employee-role")).selectItem("Employee");
        test(select(dialog, "employee-department")).selectItem(department.getName());
        test(region).selectItem(USA);
        test(button(dialog, "employee-save")).click();

        assertFalse(dialog.isOpened());
        assertEquals(usa, employees.findByEmailIgnoreCase("zoe.zimmer@example.com").orElseThrow().getRegionId());
    }

    @Test
    void mainFlow_changingTheRegionOfAnEmployeeIsAuditedWithOldAndNewRegion() {
        var row = employeeAdmin.update(carol, alice, new EmployeeInput(null, "Alice", "Employee", Role.EMPLOYEE, carol,
                department.getId(), bavaria));
        assertEquals(bavaria, row.regionId());
        assertEquals(BAVARIA, row.regionName());

        AuditLogEntry entry = auditLog.findAllByOrderByIdAsc().getLast();
        assertEquals("Employee", entry.getEntityType());
        assertEquals(AuditAction.UPDATE, entry.getAction());
        assertTrue(entry.getOldValues().contains("\"region\":\"Default\""), entry.getOldValues());
        assertTrue(entry.getNewValues().contains("\"region\":\"" + BAVARIA + "\""), entry.getNewValues());
    }

    @Test
    @WithEmployee(email = "alice.employee@example.com", firstName = "Alice", lastName = "Employee", role = Role.EMPLOYEE)
    void mainFlow_anEmployeeSeesOnlyTheHolidaysOfTheirOwnRegionAndItsName() {
        MonthlyTimesheetView view = openMonth();

        String page = text(view);
        assertTrue(page.contains("Public holidays: Default"), page);
        assertTrue(page.contains("Public holiday: Founders Day"), page);
        assertFalse(page.contains("Labor Day") || page.contains("Bavarian Day"), page);
    }

    @Test
    @WithEmployee(email = "bob.manager@example.com", firstName = "Bob", lastName = "Manager", role = Role.MANAGER)
    void mainFlow_aManagerSeesTheHolidaysOfTheEmployeesRegionNotOfTheirOwn() {
        timesheets.getOrCreate(dana, SEPTEMBER);

        EmployeeTimesheetView view = openEmployeeTimesheet(dana);

        String page = text(view);
        assertTrue(page.contains("Public holidays: " + USA), page);
        assertTrue(page.contains("Public holiday: Labor Day"), page);
        assertFalse(page.contains("Bavarian Day") || page.contains("Founders Day"),
                "Bob's own region (Bavaria) is not the region of Dana: " + page);
    }

    @Test
    @WithEmployee(email = "bob.manager@example.com", firstName = "Bob", lastName = "Manager", role = Role.MANAGER)
    void mainFlow_theReviewOfASubmittedTimesheetShowsTheHolidaysOfTheEmployeesRegion() {
        long sheet = timesheets.getOrCreate(dana, SEPTEMBER).getId();
        jdbc.update("update timesheet set status = 'SUBMITTED', submitted_at = ? where id = ?",
                java.sql.Timestamp.from(NOW), sheet);
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login");
        UI.getCurrent().navigate(TimesheetReviewView.class, sheet);
        TimesheetReviewView view = find(TimesheetReviewView.class).single();

        String page = text(view);
        assertTrue(page.contains("Public holidays: " + USA) && page.contains("Public holiday: Labor Day"), page);
        assertFalse(page.contains("Bavarian Day"), page);
    }

    // --- Alternative Flows ----------------------------------------------------------------------

    @Test
    void af1_aRegionNameMustBeThereShortEnoughAndNewIgnoringCase() {
        for (String empty : new String[] { null, "", "   " }) {
            assertTrue(assertThrows(RegionValidationException.class, () -> regionService.add(carol, empty))
                    .has(Problem.NAME_REQUIRED), "Empty: " + empty);
        }
        assertTrue(assertThrows(RegionValidationException.class, () -> regionService.add(carol, "x".repeat(101)))
                .has(Problem.NAME_TOO_LONG));
        regionService.add(carol, "x".repeat(100));
        for (String duplicate : new String[] { "usa", " USA ", "Usa" }) {
            assertTrue(assertThrows(RegionValidationException.class, () -> regionService.add(carol, duplicate))
                    .has(Problem.NAME_TAKEN), duplicate);
        }
        RegionRow usaRow = row(regionService.list(carol), USA);
        assertTrue(assertThrows(RegionValidationException.class, () -> regionService.rename(carol, bavaria, "usa", null))
                .has(Problem.NAME_TAKEN), "Renaming to the name of another region");
        assertEquals("United States", regionService.rename(carol, usa, "United States", usaRow.version()).getName());
        assertEquals("UNITED STATES", regionService.rename(carol, usa, "UNITED STATES", null).getName(),
                "A region may change the case of its own name");
    }

    @Test
    void af1_theDialogShowsTheProblemAtTheNameField() {
        openHolidays();
        test(button("manage-regions")).click();
        Dialog dialog = find(Dialog.class).single();

        test(textField(dialog, "region-name-new")).setValue("usa");
        test(button(dialog, "region-add")).click();

        assertTrue(textField(dialog, "region-name-new").isInvalid());
        assertEquals("A region with this name already exists.", textField(dialog, "region-name-new").getErrorMessage());
        assertEquals(3, regionRows(dialog).size(), "Nothing was added");

        test(textField(dialog, "region-name-new")).setValue("");
        test(button(dialog, "region-add")).click();
        assertEquals("Please enter a region name.", textField(dialog, "region-name-new").getErrorMessage());
    }

    @Test
    void af2_aDateCanBeAHolidayOnceInEveryRegionButNotTwiceInOne() {
        holidayService.add(carol, bavaria, LocalDate.of(2026, 9, 7), "Same day as in the USA");

        HolidayValidationException taken = assertThrows(HolidayValidationException.class,
                () -> holidayService.add(carol, usa, LocalDate.of(2026, 9, 7), "Labor Day again"));
        assertTrue(taken.has(HolidayValidationException.Problem.DATE_TAKEN));
        assertEquals(LocalDate.of(2026, 9, 7), taken.getDate());
        assertEquals(1, holidays.findAll().stream().filter(h -> h.getRegionId() == usa
                && h.getDate().equals(LocalDate.of(2026, 9, 7))).count());
        assertThrows(DataIntegrityViolationException.class,
                () -> holidays.saveAndFlush(new PublicHoliday(usa, LocalDate.of(2026, 9, 7), "Duplicate")),
                "The database refuses it as well");
    }

    @Test
    void af2_movingAHolidayToADateOfItsRegionIsRefusedButOtherRegionsDoNotMatter() {
        long laborDay = holidays.findByRegionIdAndDate(usa, LocalDate.of(2026, 9, 7)).orElseThrow().getId();
        holidayService.add(carol, usa, LocalDate.of(2026, 9, 20), "Second holiday of the USA");

        assertThrows(HolidayValidationException.class,
                () -> holidayService.update(carol, laborDay, LocalDate.of(2026, 9, 20), "Labor Day"));
        PublicHoliday moved = holidayService.update(carol, laborDay, LocalDate.of(2026, 9, 15), "Labor Day");
        assertEquals(LocalDate.of(2026, 9, 15), moved.getDate(), "Bavaria has 9-20 and Default has 9-15: not the USA");
    }

    @Test
    void af3_aRegionInUseCannotBeDeletedAndTheReasonSaysWhy() {
        assertEquals(Reason.EMPLOYEES, assertThrows(RegionInUseException.class, () -> regionService.delete(carol, usa))
                .getReason(), "Dana works there");
        assertEquals(1, assertThrows(RegionInUseException.class, () -> regionService.delete(carol, usa)).getCount());
        jdbc.update("update employee set region_id = ? where id = ?", Region.DEFAULT_ID, dana);
        RegionInUseException holidaysLeft = assertThrows(RegionInUseException.class, () -> regionService.delete(carol, usa));
        assertEquals(Reason.HOLIDAYS, holidaysLeft.getReason());
        assertEquals(1, holidaysLeft.getCount());
        assertTrue(regions.existsById(usa), "Nothing was deleted");

        jdbc.update("delete from public_holiday where region_id = ?", usa);
        regionService.delete(carol, usa);
        assertFalse(regions.existsById(usa));
    }

    @Test
    void af3_theLastRegionCannotBeDeleted() {
        jdbc.update("delete from public_holiday");
        jdbc.update("update employee set region_id = ?", Region.DEFAULT_ID);
        regionService.delete(carol, usa);
        regionService.delete(carol, bavaria);

        assertEquals(Reason.LAST_REGION, assertThrows(RegionInUseException.class,
                () -> regionService.delete(carol, Region.DEFAULT_ID)).getReason());
        assertTrue(regions.existsById(Region.DEFAULT_ID));
        assertThrows(RegionNotFoundException.class, () -> regionService.delete(carol, 999_999L));
    }

    @Test
    void af3_theDialogExplainsWhyARegionCannotBeDeleted() {
        openHolidays();
        test(button("manage-regions")).click();
        Dialog dialog = find(Dialog.class).single();

        test(button(regionRow(dialog, USA), "region-delete")).click();
        test(button("region-delete-confirm")).click();

        assertTrue(text(dialog).contains("This region cannot be deleted: 1 employee is assigned to it."), text(dialog));
        assertTrue(regions.existsById(usa));
    }

    @Test
    void af4_aRenameBasedOnAnOldVersionIsRefusedAndStoresNothing() {
        RegionRow opened = row(regionService.list(carol), USA);
        regionService.rename(carol, usa, "United States", opened.version());
        int audits = auditLog.findAllByOrderByIdAsc().size();

        assertThrows(EditConflictException.class, () -> regionService.rename(carol, usa, "America", opened.version()));

        assertEquals("United States", regions.findById(usa).orElseThrow().getName());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size(), "No audit entry either");
    }

    @Test
    void af4_theDialogTellsSomebodyElseChangedTheRegion() {
        openHolidays();
        test(button("manage-regions")).click();
        Dialog dialog = find(Dialog.class).single();
        test(button(regionRow(dialog, USA), "region-rename")).click();
        Dialog rename = dialogWith("region-name");
        regionService.rename(carol, usa, "United States", null); // somebody else, meanwhile
        test(textField(rename, "region-name")).setValue("America");

        test(button(rename, "region-rename-save")).click();

        assertFalse(rename.isOpened());
        assertTrue(text(dialog).contains("This region was changed by someone else in the meantime. Nothing was saved."),
                text(dialog));
        assertEquals("United States", regions.findById(usa).orElseThrow().getName());
    }

    @Test
    void af5_anEmployeeNeedsAKnownRegion() {
        EmployeeInput noRegion = new EmployeeInput("new.person@example.com", "New", "Person", Role.EMPLOYEE, null,
                department.getId(), null);
        assertTrue(assertThrows(EmployeeValidationException.class, () -> employeeAdmin.create(carol, noRegion))
                .has(EmployeeValidationException.Problem.REGION_REQUIRED));
        assertTrue(assertThrows(EmployeeValidationException.class, () -> employeeAdmin.update(carol, alice,
                new EmployeeInput(null, "Alice", "Employee", Role.EMPLOYEE, null, department.getId(), null)))
                .has(EmployeeValidationException.Problem.REGION_REQUIRED));
        assertTrue(assertThrows(EmployeeValidationException.class, () -> employeeAdmin.create(carol,
                new EmployeeInput("new.person@example.com", "New", "Person", Role.EMPLOYEE, null, department.getId(),
                        999_999L))).has(EmployeeValidationException.Problem.REGION_UNKNOWN));
        assertTrue(employees.findByEmailIgnoreCase("new.person@example.com").isEmpty());
    }

    @Test
    void af6_aDatabaseErrorStoresNothingAndSaysSo() {
        Mockito.doThrow(new DataAccessResourceFailureException("database down")).when(regions)
                .saveAndFlush(any(Region.class));
        int audits = auditLog.findAllByOrderByIdAsc().size();
        openHolidays();
        test(button("manage-regions")).click();
        Dialog dialog = find(Dialog.class).single();

        test(textField(dialog, "region-name-new")).setValue("Spain");
        test(button(dialog, "region-add")).click();

        assertTrue(text(dialog).contains("Unable to save changes. Please try again."), text(dialog));
        Mockito.reset(regions);
        assertTrue(regions.findByNameIgnoreCase("Spain").isEmpty());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
        assertTrue(dialog.isOpened(), "The dialog stays open: try again");
    }

    @Test
    void af7_cancellingTheRenameChangesNothing() {
        openHolidays();
        test(button("manage-regions")).click();
        Dialog dialog = find(Dialog.class).single();
        test(button(regionRow(dialog, USA), "region-rename")).click();
        Dialog rename = dialogWith("region-name");
        test(textField(rename, "region-name")).setValue("Elsewhere");
        int audits = auditLog.findAllByOrderByIdAsc().size();

        test(button(rename, "region-rename-cancel")).click();

        assertFalse(rename.isOpened());
        assertEquals(USA, regions.findById(usa).orElseThrow().getName());
        assertEquals(audits, auditLog.findAllByOrderByIdAsc().size());
    }

    @Test
    void af8_whenTheRegionOfAnEmployeeChangesAllMonthsFollow() {
        MonthlyTimesheet before = monthly.load(dana, SEPTEMBER, ZoneOffset.UTC);
        assertEquals("Labor Day", before.days().get(6).holidayName());
        assertEquals(USA, before.regionName());

        employeeAdmin.update(carol, dana, new EmployeeInput(null, "Dana", "American", Role.EMPLOYEE, bob,
                department.getId(), bavaria));

        MonthlyTimesheet after = monthly.load(dana, SEPTEMBER, ZoneOffset.UTC);
        assertNull(after.days().get(6).holidayName(), "The holidays of the USA are gone");
        assertEquals("Bavarian Day", after.days().get(19).holidayName());
        assertEquals(BAVARIA, after.regionName());
        assertEquals(before.status(), after.status(), "The timesheet itself is untouched");
        assertEquals(before.totalWorked(), after.totalWorked());
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_onlyActiveAdministratorsManageRegionsAndHolidays() {
        for (long notAdmin : List.of(bob, alice)) {
            assertThrows(AdminOnlyException.class, () -> regionService.list(notAdmin));
            assertThrows(AdminOnlyException.class, () -> regionService.add(notAdmin, "Spain"));
            assertThrows(AdminOnlyException.class, () -> regionService.rename(notAdmin, usa, "Spain", null));
            assertThrows(AdminOnlyException.class, () -> regionService.delete(notAdmin, usa));
            assertThrows(AdminOnlyException.class,
                    () -> holidayService.add(notAdmin, usa, LocalDate.of(2026, 12, 25), "Christmas"));
            assertThrows(AdminOnlyException.class, () -> employeeAdmin.regions(notAdmin));
        }
        jdbc.update("update employee set is_active = false where id = ?", carol);
        try {
            assertThrows(AdminOnlyException.class, () -> regionService.add(carol, "Spain"));
        } finally {
            jdbc.update("update employee set is_active = true where id = ?", carol);
        }
        assertTrue(regions.findByNameIgnoreCase("Spain").isEmpty());
    }

    @Test
    void br02_theMigrationCreatedTheRegionDefaultForEverybodyAndEverything() {
        assertEquals(1L, Region.DEFAULT_ID);
        assertEquals("Default", jdbc.queryForObject("select name from region where id = 1", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from employee where region_id is null", Integer.class),
                "Every employee has a region");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into public_holiday (holiday_date, name, created_at) values (DATE '2026-01-01', 'No region', "
                        + "CURRENT_TIMESTAMP)"), "A holiday without a region is impossible");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update employee set region_id = 999999 where id = ?", alice),
                "An employee needs an existing region");
    }

    @Test
    void br03_theRegionOfAHolidayNeverChanges() {
        PublicHoliday laborDay = holidays.findByRegionIdAndDate(usa, LocalDate.of(2026, 9, 7)).orElseThrow();

        PublicHoliday updated = holidayService.update(carol, laborDay.getId(), LocalDate.of(2026, 9, 8), "Labor Day (moved)",
                laborDay.getVersion());

        assertEquals(usa, updated.getRegionId());
        assertEquals(usa, holidays.findById(laborDay.getId()).orElseThrow().getRegionId());
    }

    @Test
    void br03_theEditFormShowsTheRegionButDoesNotLetItChange() {
        PublicHolidayView view = openHolidays();
        choose(USA);

        test(button("edit-holiday")).click();

        Dialog dialog = find(Dialog.class).single();
        assertEquals(USA, textField(dialog, "holiday-region-shown").getValue());
        assertTrue(textField(dialog, "holiday-region-shown").isReadOnly());
        assertNotNull(view);
    }

    @Test
    void br04_aRegionCannotBeAddedTwiceEvenWhenTwoAdministratorsTryAtTheSameMoment() {
        // The check finds no such region, and then the other administrator's region is stored first.
        jdbc.update("insert into region (name, version, created_at, updated_at) values ('Spain', 0, CURRENT_TIMESTAMP, "
                + "CURRENT_TIMESTAMP)");
        Mockito.doReturn(java.util.Optional.empty()).when(regions).findByNameIgnoreCase("Spain");

        RegionValidationException taken = assertThrows(RegionValidationException.class,
                () -> regionService.add(carol, "Spain"));

        assertTrue(taken.has(Problem.NAME_TAKEN));
    }

    @Test
    void br06_everybodyLooksAtTheRegionOfTheEmployeeWhoseTimesheetItIs() {
        for (long viewer : List.of(bob, alice, carol)) {
            // The service knows only the employee: it does not matter who asks.
            assertEquals("Labor Day", monthly.load(dana, SEPTEMBER, ZoneOffset.UTC).days().get(6).holidayName(), "" + viewer);
        }
        assertNull(monthly.load(alice, SEPTEMBER, ZoneOffset.UTC).days().get(6).holidayName());
        assertEquals("Founders Day", monthly.load(alice, SEPTEMBER, ZoneOffset.UTC).days().get(14).holidayName());
        assertNull(monthly.load(bob, SEPTEMBER, ZoneOffset.UTC).days().get(14).holidayName());
        assertEquals("Bavarian Day", monthly.load(bob, SEPTEMBER, ZoneOffset.UTC).days().get(19).holidayName());
    }

    @Test
    void br08_holidaysNeverChangeWorkedTime() {
        MonthlyTimesheet withHolidays = monthly.load(dana, SEPTEMBER, ZoneOffset.UTC);
        jdbc.update("delete from public_holiday");
        MonthlyTimesheet without = monthly.load(dana, SEPTEMBER, ZoneOffset.UTC);

        assertEquals(withHolidays.totalWorked(), without.totalWorked());
        assertEquals(withHolidays.totalBreaks(), without.totalBreaks());
    }

    // --- helpers --------------------------------------------------------------------------------

    private long person(String email, String first, String last, Role role, long regionId, Long managerId) {
        Employee employee = employees.findByEmailIgnoreCase(email)
                .orElseGet(() -> new Employee(email, first, last, role, department.getId()));
        employee.setRole(role);
        employee.setActive(true);
        employee.setRegionId(regionId);
        employee.setManagerId(managerId);
        return employees.save(employee).getId();
    }

    private static RegionRow row(List<RegionRow> rows, String name) {
        return rows.stream().filter(row -> row.name().equals(name)).findFirst().orElseThrow();
    }

    private void locale() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login"); // every call builds a fresh page
    }

    private PublicHolidayView openHolidays() {
        locale();
        UI.getCurrent().navigate(PublicHolidayView.class);
        return find(PublicHolidayView.class).single();
    }

    private EmployeeManagementView openEmployees() {
        locale();
        UI.getCurrent().navigate(EmployeeManagementView.class);
        return find(EmployeeManagementView.class).single();
    }

    private MonthlyTimesheetView openMonth() {
        locale();
        UI.getCurrent().navigate(MonthlyTimesheetView.ROUTE, QueryParameters.of("month", "2026-09"));
        return find(MonthlyTimesheetView.class).single();
    }

    private EmployeeTimesheetView openEmployeeTimesheet(long employeeId) {
        locale();
        UI.getCurrent().navigate(EmployeeTimesheetView.class, employeeId, QueryParameters.of("month", "2026-09"));
        return find(EmployeeTimesheetView.class).single();
    }

    @SuppressWarnings("unchecked")
    private Select<RegionRow> regionSelect() {
        return find(Select.class).all().stream().filter(select -> "holiday-region".equals(select.getTestId()))
                .findFirst().orElseThrow();
    }

    private List<String> regionNames() {
        return regionSelect().getListDataView().getItems().map(RegionRow::name).toList();
    }

    private void choose(String regionName) {
        regionSelect().setValue(regionSelect().getListDataView().getItems()
                .filter(region -> region.name().equals(regionName)).findFirst().orElseThrow());
    }

    @SuppressWarnings("unchecked")
    private Select<Integer> yearSelect() {
        return find(Select.class).all().stream().filter(select -> "holiday-year".equals(select.getTestId()))
                .findFirst().orElseThrow();
    }

    private List<Div> holidayRows() {
        return find(Div.class).all().stream().filter(div -> "holiday-row".equals(div.getTestId())).toList();
    }

    private List<Div> regionRows(Component scope) {
        return find(Div.class).from(scope).all().stream().filter(div -> "region-row".equals(div.getTestId())).toList();
    }

    private Div regionRow(Component scope, String name) {
        return regionRows(scope).stream().filter(div -> text(div).trim().startsWith(name + " Employees")).findFirst()
                .orElseThrow(() -> new AssertionError("No region row " + name));
    }

    /** The open dialog that has a text field with this test id (the dialogs of the page can be stacked). */
    private Dialog dialogWith(String textFieldTestId) {
        return find(Dialog.class).all().stream().filter(dialog -> find(TextField.class).from(dialog).all().stream()
                .anyMatch(field -> textFieldTestId.equals(field.getTestId()))).findFirst()
                .orElseThrow(() -> new AssertionError("No dialog with " + textFieldTestId));
    }

    private TextField textField(Component scope, String testId) {
        return find(TextField.class).from(scope).all().stream().filter(field -> testId.equals(field.getTestId()))
                .findFirst().orElseThrow(() -> new AssertionError("No field " + testId));
    }

    @SuppressWarnings("unchecked")
    private Select<Object> select(Component scope, String testId) {
        return (Select<Object>) find(Select.class).from(scope).all().stream()
                .filter(select -> testId.equals(select.getTestId())).findFirst()
                .orElseThrow(() -> new AssertionError("No select " + testId));
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
