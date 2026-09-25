package com.stubu.specdriven.usecases.uc005_view_monthly_timesheet;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.holiday.PublicHoliday;
import com.stubu.specdriven.holiday.PublicHolidayRepository;
import com.stubu.specdriven.testsupport.E2ETest;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timetracking.TimeEntryService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-005 View Monthly Timesheet in a real browser: navigation, the calendar-like timeline with weekends and
 * holidays, month switching, the table view and the status area at the supported screen sizes. The behaviour
 * behind it is covered by {@code UC005ViewMonthlyTimesheet}. It is 2026-09-23 17:00 UTC (browser and server).
 */
class UC005ViewMonthlyTimesheetE2E extends E2ETest {

    private static final Instant NOW = Instant.parse("2026-09-23T17:00:00Z");

    @Autowired
    TimeEntryService service;
    @Autowired
    TimesheetService timesheetService;
    @Autowired
    PublicHolidayRepository holidays;
    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    /** Alice worked on 1 and 2 September and on the holiday 15 September. */
    @BeforeEach
    void recordSomeDays() {
        jdbc.update("delete from timesheet");
        jdbc.update("delete from public_holiday");
        long alice = employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true).getId();
        record(alice, "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record(alice, "2026-09-01T13:00:00Z", "2026-09-01T17:00:00Z");
        record(alice, "2026-09-02T09:00:00Z", "2026-09-02T10:30:00Z");
        record(alice, "2026-09-15T08:00:00Z", "2026-09-15T12:00:00Z");
        record(alice, "2026-08-10T08:00:00Z", "2026-08-10T09:00:00Z");
        holidays.save(new PublicHoliday(LocalDate.of(2026, 9, 15), "Founders Day"));
        clock.set(NOW);
    }

    private void record(long employeeId, String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        service.checkOutWithMissingCheckIn(employeeId, Instant.parse(checkIn));
    }

    private void openTimesheet(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        page.navigate(url("/timesheet"));
        page.locator(".day-row").first().waitFor();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theNavigationLeadsFromTodayToTheTimesheetAndBack(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        assertNavigationLayout(viewport, ".home-welcome");
        if (viewport.width() < 1024) {
            page.getByTestId("drawer-toggle").click(); // the drawer is closed on narrow screens
        }
        assertThat(page.getByTestId("nav-today")).isVisible();
        screenshot("navigation-" + viewport.name());

        page.getByTestId("nav-timesheet").click();

        page.waitForURL(Pattern.compile(".*/timesheet"));
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("My Timesheet"))).isVisible();
        if (viewport.width() < 1024) {
            page.getByTestId("drawer-toggle").click();
        }
        page.getByTestId("nav-today").click();
        page.waitForURL(url("/"));
        assertThat(page.getByTestId("check-in")).isVisible();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theMonthTimesheetIsReadableAndFitsEveryScreenSize(Viewport viewport) {
        openTimesheet(viewport);

        assertThat(page.locator(".day-row")).hasCount(30);
        assertThat(page.getByTestId("month-total")).hasText("Total hours: 9h 30m".replace("9h 30m", "13h 30m"));
        assertThat(page.getByTestId("timesheet-status")).hasText("Not submitted");
        assertNoHorizontalOverflow();
        // Weekends and the holiday are marked with text, and also look different from ordinary days.
        assertThat(page.locator(".day-weekend .day-weekend-tag")).hasCount(8);
        assertThat(page.getByTestId("holiday")).hasText("Public holiday: Founders Day");
        String ordinary = style(".day-row:not(.day-weekend):not(.day-holiday):not(.day-current)", "backgroundImage");
        assertEquals("none", ordinary);
        assertNotEquals("none", style(".day-weekend", "backgroundImage"), "Weekends are hatched");
        assertNotEquals("rgba(0, 0, 0, 0)", style(".day-holiday", "backgroundColor"), "Holidays are tinted");
        // September 1: 08:00-12:00 and 13:00-17:00 on the 00-24 hour scale.
        Locator firstTrack = page.locator(".day-row .timeline-track").first();
        var track = firstTrack.boundingBox();
        List<Locator> bars = firstTrack.locator(".timeline-bar").all();
        assertEquals(2, bars.size());
        assertBar(bars.get(0), track, 8 * 60, 4 * 60);
        assertBar(bars.get(1), track, 13 * 60, 4 * 60);
        assertThat(page.locator(".day-row").first()).containsText("Total 8h 0m, break 1h 0m");
        assertReadable(".day-label");
        assertReadable(".day-summary");
        assertReadable(".day-tag");
        assertReadable(".day-none");
        assertReadable(".timesheet-status");
        screenshot("month-" + viewport.name());
    }

    @Test
    void theMonthCanBeSwitchedWithTheArrows() {
        openTimesheet(DESKTOP);
        assertThat(page.getByTestId("next-month")).isDisabled();

        page.getByTestId("previous-month").click();

        page.waitForURL(Pattern.compile(".*/timesheet\\?month=2026-08"));
        assertThat(page.locator(".day-row")).hasCount(31);
        assertThat(page.getByTestId("month-total")).hasText("Total hours: 1h 0m");
        assertThat(page.getByTestId("month-select")).containsText("August 2026");
        assertThat(page.getByTestId("next-month")).isEnabled();
        screenshot("month-august");

        page.getByTestId("next-month").click();

        page.waitForURL(Pattern.compile(".*/timesheet\\?month=2026-09"));
        assertThat(page.locator(".day-row")).hasCount(30);
        assertThat(page.getByTestId("next-month")).isDisabled();
    }

    @Test
    void aMonthWithoutEntriesShowsAHintAndAZeroTotal() {
        openTimesheet(MOBILE);

        page.getByTestId("previous-month").click();
        page.waitForURL(Pattern.compile(".*month=2026-08"));
        page.getByTestId("previous-month").click(); // July
        page.waitForURL(Pattern.compile(".*month=2026-07"));
        page.getByTestId("previous-month").click(); // June: nothing recorded

        assertThat(page.getByTestId("empty-month")).hasText("No time entries recorded for June 2026.");
        assertThat(page.getByTestId("month-total")).hasText("Total hours: 0h 0m");
        assertReadable(".time-empty");
        assertNoHorizontalOverflow();
        screenshot("month-empty-mobile");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theTableViewListsTheDaysAndFitsTheScreen(Viewport viewport) {
        openTimesheet(viewport);

        page.getByText("Table", new Page.GetByTextOptions().setExact(true)).click();

        Locator grid = page.getByTestId("month-table");
        assertThat(grid).isVisible();
        assertThat(page.locator(".month-timeline")).isHidden();
        assertThat(grid).containsText("Founders Day");
        assertThat(grid).containsText("Weekend");
        assertThat(grid).containsText(Pattern.compile("1:00\\sPM"));
        assertNoHorizontalOverflow();
        screenshot("month-table-" + viewport.name());
    }

    @Test
    void theStatusAreaShowsWhoApprovedOrWhyItWasRejected() {
        long bob = employee("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, true).getId();
        timesheetService.getOrCreate(alice(), YearMonth.of(2026, 9));

        jdbc.update("update timesheet set status = 'APPROVED', approved_at = ?, approved_by = ?",
                java.sql.Timestamp.from(Instant.parse("2026-09-20T09:00:00Z")), bob);
        openTimesheet(DESKTOP);
        assertThat(page.getByTestId("status-text")).hasText("Approved on Sep 20, 2026 by Bob Manager");
        assertThat(page.getByTestId("edit-entry")).hasCount(0);
        assertThat(page.getByTestId("submit-timesheet")).isHidden();
        assertReadable(".status-note");
        screenshot("status-approved");

        jdbc.update("update timesheet set status = 'REJECTED', approved_at = null, approved_by = null, "
                + "rejected_at = ?, rejected_by = ?, rejection_reason = ?",
                java.sql.Timestamp.from(Instant.parse("2026-09-21T09:00:00Z")), bob, "Please fix Monday");
        page.reload();
        page.locator(".day-row").first().waitFor();
        assertThat(page.getByTestId("status-text")).hasText("Rejected on Sep 21, 2026 with reason: Please fix Monday");
        assertThat(page.getByTestId("submit-timesheet")).isDisabled();
        assertThat(page.getByTestId("submit-timesheet")).hasText("Resubmit timesheet");
        screenshot("status-rejected");
    }

    @Test
    void anEntryCanBeCorrectedFromTheTimesheet() {
        openTimesheet(DESKTOP);

        page.getByTestId("edit-entry").first().click();
        page.locator("vaadin-time-picker input").nth(1).fill("13:00");
        page.locator("vaadin-time-picker input").nth(1).press("Enter");
        page.getByTestId("edit-save").click();

        assertThat(page.locator(".time-message")).hasText("Time entry updated.");
        assertThat(page.locator(".day-row").first()).containsText("Total 9h 0m, break 0h 0m");
        assertThat(page.getByTestId("month-total")).hasText("Total hours: 14h 30m");
        screenshot("month-edited");
    }

    private long alice() {
        return employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true).getId();
    }

    private String style(String selector, String property) {
        return (String) page.evaluate("([selector, property]) => getComputedStyle(document.querySelector(selector))[property]",
                List.of(selector, property));
    }

    /** The bar starts and is as long as expected (minutes of the day), within a small tolerance. */
    private static void assertBar(Locator bar, com.microsoft.playwright.options.BoundingBox track, double startMinute,
            double lengthMinutes) {
        var box = bar.boundingBox();
        assertEquals(startMinute, (box.x - track.x) / track.width * 24 * 60, 8.0, "Bar start");
        assertEquals(lengthMinutes, box.width / track.width * 24 * 60, 8.0, "Bar length");
        assertTrue(box.width > 0);
    }
}
