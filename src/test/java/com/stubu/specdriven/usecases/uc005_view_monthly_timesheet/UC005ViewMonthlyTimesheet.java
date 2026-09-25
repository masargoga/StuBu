package com.stubu.specdriven.usecases.uc005_view_monthly_timesheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static com.stubu.specdriven.testsupport.ViewTexts.text;
import static com.stubu.specdriven.testsupport.ViewTexts.normalize;

import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.holiday.PublicHoliday;
import com.stubu.specdriven.holiday.PublicHolidayRepository;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheet;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetService;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetView;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.QueryParameters;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * UC-005 View Monthly Timesheet. It is 2026-09-23 17:00 UTC (September 2026 starts on a Tuesday); work periods
 * are recorded through the service, timesheet status and public holidays are set directly, as UC-006, UC-007 and
 * UC-012 will do later.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee
class UC005ViewMonthlyTimesheet extends SpringBrowserlessTest {

    private static final Instant NOW = Instant.parse("2026-09-23T17:00:00Z");
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final String ALICE = "alice.employee@example.com";

    @Autowired
    MutableClock clock;
    @Autowired
    TimeEntryService service;
    @Autowired
    MonthlyTimesheetService monthly;
    @Autowired
    TimesheetService timesheetService;
    @MockitoSpyBean
    TimeEntryRepository timeEntries;
    @Autowired
    TimesheetRepository timesheets;
    @Autowired
    PublicHolidayRepository holidays;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    JdbcTemplate jdbc;

    private long alice;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(timeEntries);
        clock.set(NOW);
        jdbc.update("delete from time_entry");
        jdbc.update("delete from timesheet");
        jdbc.update("delete from public_holiday");
        alice = employees.findByEmailIgnoreCase(ALICE).orElseThrow().getId();
    }

    @AfterEach
    void resetRepository() {
        Mockito.reset(timeEntries);
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_showsTheCurrentMonthAndCreatesADraftTimesheetOnFirstAccess() {
        assertEquals(0, timesheets.count());

        MonthlyTimesheetView view = openSheet(null);

        assertTrue(text(view).strip().startsWith("My Timesheet"), text(view));
        assertEquals(YearMonth.of(2026, 9), monthSelect().getValue());
        assertEquals(30, dayRows(view).size(), "One row per day of September");
        assertEquals(1, timesheets.count(), "The draft timesheet was created");
        Timesheet created = timesheets.findAll().getFirst();
        assertEquals(TimesheetStatus.DRAFT, created.getStatus());
        assertEquals(YearMonth.of(2026, 9), created.getPeriod());
        assertEquals(alice, created.getEmployeeId());
        assertTrue(text(view).contains("Not submitted"), text(view));
        openSheet(null); // opening it again does not create another one
        assertEquals(1, timesheets.count());
    }

    @Test
    void mainFlow_groupsEntriesByDayWithDailyTotalsAndTheMonthlyTotal() {
        record("2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record("2026-09-01T13:00:00Z", "2026-09-01T17:00:00Z");
        record("2026-09-02T09:00:00Z", "2026-09-02T10:30:00Z");

        MonthlyTimesheetView view = openSheet(null);

        List<Div> rows = dayRows(view);
        assertTrue(text(rows.get(0)).contains("Tue 1"), text(rows.get(0)));
        assertTrue(text(rows.get(0)).contains("Total 8h 0m, break 1h 0m"), text(rows.get(0)));
        assertTrue(text(rows.get(0)).contains("8:00 AM – 12:00 PM, 4h 0m"), text(rows.get(0)));
        assertTrue(text(rows.get(0)).contains("1:00 PM – 5:00 PM, 4h 0m"), text(rows.get(0)));
        assertTrue(text(rows.get(1)).contains("Total 1h 30m, break 0h 0m"), text(rows.get(1)));
        assertTrue(text(rows.get(2)).contains("No entry"), "Days without entries are marked: " + text(rows.get(2)));
        assertEquals(28, rows.stream().filter(row -> text(row).contains("No entry")).count());
        assertTrue(text(view).contains("Total hours: 9h 30m"), text(view));
        assertTrue(text(view).contains("Break: 1h 0m"), text(view));
        assertFalse(text(view).contains("No time entries recorded for"), "Not an empty month");
    }

    @Test
    void mainFlow_weekendsAndPublicHolidaysAreMarkedWithText() {
        holidays.save(new PublicHoliday(java.time.LocalDate.of(2026, 9, 15), "Founders Day"));
        record("2026-09-15T08:00:00Z", "2026-09-15T12:00:00Z"); // worked on the holiday

        MonthlyTimesheetView view = openSheet(null);

        List<Div> rows = dayRows(view);
        List<String> weekendDays = rows.stream().filter(row -> text(row).contains("Weekend"))
                .map(row -> row.getElement().getAttribute("data-date")).toList();
        assertEquals(List.of("2026-09-05", "2026-09-06", "2026-09-12", "2026-09-13", "2026-09-19", "2026-09-20",
                "2026-09-26", "2026-09-27"), weekendDays);
        Div holiday = rows.get(14);
        assertTrue(holiday.hasClassName("day-holiday"));
        assertTrue(text(holiday).contains("Public holiday: Founders Day"), text(holiday));
        assertEquals(1, rows.stream().filter(row -> row.hasClassName("day-holiday")).count());
        assertTrue(rows.get(4).hasClassName("day-weekend"));
        assertTrue(text(rows.get(22)).contains("Today"), "Today is marked: " + text(rows.get(22)));
    }

    @Test
    void mainFlow_showsTheTimesheetStatusAndTheActionsThatGoWithIt() {
        MonthlyTimesheetView draft = openSheet(null);
        assertTrue(text(draft).contains("Not submitted"), text(draft));
        assertFalse(button("submit-timesheet").isEnabled(), "Submitting is UC-006");
        assertEquals("Submit timesheet", button("submit-timesheet").getText());

        setStatus(TimesheetStatus.SUBMITTED);
        MonthlyTimesheetView submitted = openSheet(null);
        assertTrue(text(submitted).contains("Awaiting approval"), text(submitted));
        assertTrue(buttons("submit-timesheet").stream().noneMatch(Component::isVisible));

        Employee bob = employee("bob.manager@example.com", Role.MANAGER);
        jdbc.update("update timesheet set status = 'APPROVED', approved_at = ?, approved_by = ?",
                java.sql.Timestamp.from(Instant.parse("2026-09-20T09:00:00Z")), bob.getId());
        MonthlyTimesheetView approved = openSheet(null);
        assertTrue(text(approved).contains("Approved on Sep 20, 2026 by Bob Manager"), text(approved));

        jdbc.update("update timesheet set status = 'REJECTED', approved_at = null, approved_by = null, "
                + "rejected_at = ?, rejected_by = ?, rejection_reason = ?",
                java.sql.Timestamp.from(Instant.parse("2026-09-21T09:00:00Z")), bob.getId(), "Please fix Monday");
        MonthlyTimesheetView rejected = openSheet(null);
        assertTrue(text(rejected).contains("Rejected on Sep 21, 2026 with reason: Please fix Monday"), text(rejected));
        assertEquals("Resubmit timesheet", button("submit-timesheet").getText());
        assertFalse(button("submit-timesheet").isEnabled(), "September is not over yet in this scenario");
    }

    @Test
    void mainFlow_entriesCanBeCorrectedAndDeletedFromTheTimesheetWhileItIsADraft() {
        record("2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record("2026-09-02T09:00:00Z", "2026-09-02T10:00:00Z");
        MonthlyTimesheetView view = openSheet(null);

        test(buttons("edit-entry").getFirst()).click();
        Dialog dialog = find(Dialog.class).single();
        find(TimePicker.class).all().get(1).setValue(LocalTime.of(13, 0));
        test(button(dialog, "edit-save")).click();

        assertTrue(text(view).contains("Time entry updated."), text(view));
        assertTrue(text(dayRows(view).getFirst()).contains("8:00 AM – 1:00 PM, 5h 0m"),
                text(dayRows(view).getFirst()));
        assertTrue(text(view).contains("Total hours: 6h 0m"), text(view));

        test(buttons("delete-entry").getFirst()).click();
        test(button(find(Dialog.class).single(), "delete-confirm")).click();

        assertTrue(text(view).contains("Time entry deleted."), text(view));
        assertTrue(text(dayRows(view).getFirst()).contains("No entry"));
        assertTrue(text(view).contains("Total hours: 1h 0m"), text(view));
    }

    @Test
    void mainFlow_theNavigationOffersTodayAndMyTimesheet() {
        openSheet(null);

        List<SideNavItem> items = find(SideNavItem.class).all();

        assertEquals(List.of("Today", "My Timesheet"), items.stream().map(SideNavItem::getLabel).toList());
        assertEquals(List.of("", "timesheet"), items.stream().map(SideNavItem::getPath).toList());
    }

    // --- AF-1: No Time Entries in Month ----------------------------------------------------------

    @Test
    void af1_aMonthWithoutEntriesShowsEveryDayEmptyAndAZeroTotal() {
        MonthlyTimesheetView view = openSheet("2026-08");

        assertEquals(31, dayRows(view).size());
        assertTrue(dayRows(view).stream().allMatch(row -> text(row).contains("No entry")));
        assertTrue(text(view).contains("No time entries recorded for August 2026."), text(view));
        assertTrue(text(view).contains("Total hours: 0h 0m"), text(view));
        assertTrue(button("submit-timesheet").isVisible(), "A draft can be submitted");
        assertEquals(TimesheetStatus.DRAFT, timesheetService.statusOf(alice, YearMonth.of(2026, 8)));
    }

    // --- AF-2: Navigate to Different Month -------------------------------------------------------

    @Test
    void af2_previousAndNextMonthReloadTheDisplay() {
        record("2026-08-10T08:00:00Z", "2026-08-10T12:00:00Z");
        record("2026-09-10T08:00:00Z", "2026-09-10T10:00:00Z");
        MonthlyTimesheetView view = openSheet(null);
        assertTrue(text(view).contains("Total hours: 2h 0m"), text(view));
        assertFalse(button("next-month").isEnabled(), "There is nothing after the current month");

        test(button("previous-month")).click();

        assertEquals(YearMonth.of(2026, 8), monthSelect().getValue());
        assertEquals(31, dayRows(view).size());
        assertTrue(text(view).contains("Total hours: 4h 0m"), text(view));
        assertTrue(button("next-month").isEnabled());
        assertEquals(2, timesheets.count(), "August got its own draft timesheet");

        test(button("next-month")).click();

        assertEquals(YearMonth.of(2026, 9), monthSelect().getValue());
        assertTrue(text(view).contains("Total hours: 2h 0m"), text(view));
    }

    @Test
    void af2_anyRecentMonthCanBePickedFromTheSelector() {
        record("2026-07-15T08:00:00Z", "2026-07-15T09:00:00Z");
        MonthlyTimesheetView view = openSheet(null);

        monthSelect().setValue(YearMonth.of(2026, 7));

        assertTrue(text(view).contains("Total hours: 1h 0m"), text(view));
        assertEquals(31, dayRows(view).size());
        List<YearMonth> offered = new ArrayList<>(monthSelect().getListDataView().getItems().toList());
        assertEquals(YearMonth.of(2026, 9), offered.getFirst(), "Newest first, nothing in the future");
        assertEquals(24, offered.size());
    }

    // --- AF-3: Database Error Loading Timesheet --------------------------------------------------

    @Test
    void af3_databaseErrorWhileLoading_showsAnErrorWithRetryAndNoTimesheet() {
        record("2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timeEntries)
                .findStartedBetween(any(), any(), any());

        MonthlyTimesheetView view = openSheet(null);

        assertTrue(text(view).contains("Unable to load timesheet. Please try again."), text(view));
        assertTrue(button("retry").isVisible());
        assertEquals(0, dayRows(view).size(), "No timesheet is shown");
        assertFalse(text(view).contains("Total hours"), text(view));

        Mockito.reset(timeEntries);
        test(button("retry")).click();

        assertFalse(text(view).contains("Unable to load timesheet"), text(view));
        assertEquals(30, dayRows(view).size());
        assertTrue(text(view).contains("Total hours: 4h 0m"), text(view));
    }

    // --- AF-4: Table View -----------------------------------------------------------------------

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void af4_theEntriesCanBeShownAsATable() {
        holidays.save(new PublicHoliday(java.time.LocalDate.of(2026, 9, 15), "Founders Day"));
        record("2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record("2026-09-01T13:00:00Z", "2026-09-01T17:00:00Z");
        MonthlyTimesheetView view = openSheet(null);
        assertTrue(find(Grid.class).all().stream().noneMatch(Component::isVisible), "Timeline is the default");

        RadioButtonGroup group = find(RadioButtonGroup.class).single();
        test(group).selectItem("Table");

        Grid grid = find(Grid.class).single();
        assertTrue(grid.isVisible());
        assertEquals(0, dayRows(view).size(), "The timeline is hidden");
        assertEquals(30, test(grid).size());
        assertEquals(List.of("Tue, 1 Sep", "8:00 AM – 12:00 PM · 1:00 PM – 5:00 PM", "8h 0m", "1h 0m"),
                row(grid, 0));
        assertEquals(List.of("Sat, 5 Sep\nWeekend", "", "", ""), row(grid, 4));
        assertEquals("Tue, 15 Sep\nPublic holiday: Founders Day", row(grid, 14).get(0));
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_theTimesheetIsCreatedOnFirstAccessEvenWhenTwoSessionsOpenItAtOnce() throws Exception {
        int sessions = 6;
        ExecutorService executor = Executors.newFixedThreadPool(sessions);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < sessions; i++) {
                results.add(executor.submit(() -> {
                    go.await();
                    return timesheetService.getOrCreate(alice, YearMonth.of(2026, 6)).getId();
                }));
            }
            go.countDown();
            for (Future<Long> result : results) {
                assertEquals(results.getFirst().get(), result.get(), "Everybody gets the same record");
            }
            assertEquals(1, timesheets.count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void br02_onlyEntriesOfTheSelectedMonthAreShown() {
        record("2026-08-31T20:00:00Z", "2026-08-31T22:00:00Z");
        record("2026-09-01T08:00:00Z", "2026-09-01T09:00:00Z");

        MonthlyTimesheetView september = openSheet("2026-09");
        assertTrue(text(september).contains("Total hours: 1h 0m"), text(september));
        MonthlyTimesheetView august = openSheet("2026-08");
        assertTrue(text(august).contains("Total hours: 2h 0m"), text(august));
    }

    @Test
    void br02_anEntryBelongsToTheMonthItStartedInInTheUsersTimeZone() {
        clock.set(Instant.parse("2026-09-01T02:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-08-31T22:30:00Z"));
        clock.set(NOW);

        MonthlyTimesheet utc = monthly.load(alice, YearMonth.of(2026, 8), UTC);
        MonthlyTimesheet berlin = monthly.load(alice, YearMonth.of(2026, 9), ZoneId.of("Europe/Berlin"));

        assertEquals(1, utc.days().getLast().day().entries().size(), "22:30Z on Aug 31 is August in UTC");
        assertEquals(1, berlin.days().getFirst().day().entries().size(), "...but 00:30 on Sep 1 in Berlin");
    }

    @Test
    void br03_theMonthlyTotalIsTheSumOfTheDailyTotalsIncludingTheOpenEntry() {
        record("2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record("2026-09-02T08:00:00Z", "2026-09-02T10:00:00Z");
        clock.set(Instant.parse("2026-09-23T15:00:00Z"));
        service.checkIn(alice);
        clock.set(NOW); // two hours on the open entry

        MonthlyTimesheet sheet = monthly.load(alice, YearMonth.of(2026, 9), UTC);

        assertEquals(java.time.Duration.ofHours(8), sheet.totalWorked());
        assertEquals(sheet.days().stream().map(day -> day.day().worked()).reduce(java.time.Duration.ZERO,
                java.time.Duration::plus), sheet.totalWorked());
    }

    @Test
    void br04_theStatusDecidesWhetherEntriesCanBeChanged() {
        record("2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        openSheet(null);
        assertEquals(1, buttons("edit-entry").size(), "Draft: editable");
        assertEquals(1, buttons("delete-entry").size());

        for (TimesheetStatus status : List.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED)) {
            setStatus(status);
            MonthlyTimesheetView view = openSheet(null);
            assertEquals(0, buttons("edit-entry").size(), status + ": view only");
            assertEquals(0, buttons("delete-entry").size(), status + ": view only");
            assertTrue(text(view).contains("cannot be edited because it has been submitted"), text(view));
        }
        setStatus(TimesheetStatus.REJECTED); // sent back to be corrected (UC-007)
        MonthlyTimesheetView rejected = openSheet(null);
        assertEquals(1, buttons("edit-entry").size(), "Rejected: editable again");
        assertEquals(1, buttons("delete-entry").size());
        assertFalse(text(rejected).contains("cannot be edited"), text(rejected));
    }

    @Test
    void br05_publicHolidaysAreForReferenceAndDoNotChangeTheTotals() {
        record("2026-09-15T08:00:00Z", "2026-09-15T12:00:00Z");
        MonthlyTimesheet without = monthly.load(alice, YearMonth.of(2026, 9), UTC);

        holidays.save(new PublicHoliday(java.time.LocalDate.of(2026, 9, 15), "Founders Day"));
        MonthlyTimesheet with = monthly.load(alice, YearMonth.of(2026, 9), UTC);

        assertEquals(without.totalWorked(), with.totalWorked());
        assertEquals("Founders Day", with.days().get(14).holidayName());
        assertEquals(java.time.Duration.ofHours(4), with.days().get(14).day().worked());
    }

    @Test
    void br06_futureMonthsAreNeverShown() {
        MonthlyTimesheetView view = openSheet("2026-10");

        assertEquals(YearMonth.of(2026, 9), monthSelect().getValue(), "Falls back to the current month");
        assertEquals(30, dayRows(view).size());
        assertFalse(button("next-month").isEnabled());
        assertThrows(IllegalArgumentException.class, () -> monthly.load(alice, YearMonth.of(2026, 10), UTC));
        assertEquals(0, timesheets.findAll().stream().filter(sheet -> sheet.getPeriod().equals(YearMonth.of(2026, 10)))
                .count(), "No timesheet is created for the future");
        openSheet("not-a-month");
        assertEquals(YearMonth.of(2026, 9), monthSelect().getValue());
        assertTrue(monthSelect().getListDataView().getItems().noneMatch(month -> month.isAfter(YearMonth.of(2026, 9))));
    }

    // --- helpers --------------------------------------------------------------------------------

    /** Records a completed work period in the past, then puts the clock back to "now". */
    private void record(String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        service.checkOutWithMissingCheckIn(alice, Instant.parse(checkIn));
        clock.set(NOW);
    }

    private void setStatus(TimesheetStatus status) {
        timesheetService.getOrCreate(alice, YearMonth.of(2026, 9));
        jdbc.update("update timesheet set status = ?", status.name());
    }

    private MonthlyTimesheetView openSheet(String month) {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        // The test UI starts on the home view; go through another view so every call builds a fresh page.
        UI.getCurrent().navigate("login");
        if (month == null) {
            UI.getCurrent().navigate(MonthlyTimesheetView.ROUTE);
        } else {
            UI.getCurrent().navigate(MonthlyTimesheetView.ROUTE, QueryParameters.of("month", month));
        }
        return find(MonthlyTimesheetView.class).single();
    }

    @SuppressWarnings("unchecked")
    private Select<YearMonth> monthSelect() {
        return find(Select.class).single();
    }

    private List<Div> dayRows(Component scope) {
        return find(Div.class).all().stream().filter(div -> div.hasClassName("day-row")).toList();
    }

    private Button button(String testId) {
        return buttons(testId).stream().findFirst().orElseThrow(() -> new AssertionError("No button " + testId));
    }

    private Button button(Component scope, String testId) {
        return find(Button.class).from(scope).all().stream().filter(button -> testId.equals(button.getTestId()))
                .findFirst().orElseThrow();
    }

    private List<Button> buttons(String testId) {
        return find(Button.class).all().stream().filter(button -> testId.equals(button.getTestId())).toList();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private List<String> row(Grid grid, int index) {
        List<String> cells = new ArrayList<>();
        for (int column = 0; column < 4; column++) {
            cells.add(normalize(test(grid).getCellText(index, column)));
        }
        return cells;
    }

    private Employee employee(String email, Role role) {
        return employees.findByEmailIgnoreCase(email).orElseGet(() -> {
            Department department = departments.findAll().stream().findFirst().orElseThrow();
            return employees.save(new Employee(email, "Bob", "Manager", role, department.getId()));
        });
    }

}
