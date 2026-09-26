package com.stubu.specdriven.usecases.uc015_add_historical_time_entries;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import com.stubu.specdriven.timesheet.TimesheetService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-015 Add Historical Time Entries in a real browser: the add buttons of the month view, the add form, its
 * confirmation and errors, and a locked month, at the supported screen sizes. The behaviour behind it is covered by
 * {@code UC015AddHistoricalTimeEntries}.
 */
class UC015AddHistoricalTimeEntriesE2E extends E2ETest {

    private static final Instant NOW = Instant.parse("2026-09-23T17:00:00Z");

    @Autowired
    TimesheetService timesheetService;
    @Autowired
    JdbcTemplate jdbc;

    private long alice;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    @BeforeEach
    void setUpMonth() {
        jdbc.update("delete from timesheet");
        jdbc.update("delete from public_holiday");
        alice = employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true).getId();
        clock.set(NOW);
    }

    private void openTimesheet(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        page.navigate(url("/timesheet"));
        page.locator(".day-row").first().waitFor();
    }

    private Locator dayButton(String date) {
        return page.locator("[data-date='" + date + "'] [data-testid=add-day-entry]");
    }

    /** An entry of the list of times that a time field opens. */
    private Locator timeItem(String timeRegex) {
        return page.locator("vaadin-time-picker-item:visible").filter(new Locator.FilterOptions().setHasText(Pattern.compile(timeRegex))).first();
    }

    /** Types a time into a time picker the way a user does. */
    private void fillTime(String testId, String time) {
        Locator input = page.locator("[data-testid=" + testId + "] input");
        input.fill(time);
        input.press("Enter");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theAddButtonsAreLargeReadableAndFitEveryScreenSize(Viewport viewport) {
        openTimesheet(viewport);

        assertThat(page.getByTestId("add-time-entry")).isVisible();
        assertThat(page.getByTestId("add-time-entry")).isEnabled();
        assertThat(page.getByTestId("add-time-entry")).containsText("Add time entry");
        assertThat(page.getByTestId("add-day-entry")).hasCount(23); // September 1 to today, the 23rd
        assertThat(dayButton("2026-09-24")).hasCount(0);
        double[] general = box("[data-testid=add-time-entry]");
        assertTrue(general[3] >= 44, "The general button is at least 44px high: " + general[3]);
        Locator first = page.getByTestId("add-day-entry").first();
        assertTrue(first.boundingBox().height >= 36, "Day buttons are easy to hit: " + first.boundingBox().height);
        assertNoHorizontalOverflow();
        screenshot("month-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theFormAddsEntriesAndConfirmsThemAtEveryScreenSize(Viewport viewport) {
        openTimesheet(viewport);
        dayButton("2026-09-10").click();

        assertThat(page.getByTestId("add-date")).isVisible();
        assertThat(page.getByTestId("add-save")).isVisible();
        assertNoHorizontalOverflow();
        var overlay = page.locator("vaadin-dialog-overlay").locator("[part=overlay]").boundingBox();
        assertTrue(overlay.x >= 0 && overlay.x + overlay.width <= viewport.width(), "The dialog fits the screen");
        screenshot("form-" + viewport.name());

        // Nothing entered: the error is shown and readable.
        page.getByTestId("add-save").click();
        assertThat(page.locator(".time-dialog-error")).containsText("Please enter the date and the times.");
        assertReadable(".time-dialog-error");
        screenshot("form-error-" + viewport.name());

        fillTime("add-check-in", "8:00 AM");
        fillTime("add-check-out", "12:00 PM");
        page.getByTestId("add-save-another").click();
        assertThat(page.getByTestId("add-success")).isVisible();
        assertThat(page.getByTestId("add-success")).containsText("Time entry added.");
        assertReadable(".time-dialog-success");
        screenshot("form-added-" + viewport.name());

        fillTime("add-check-in", "1:00 PM");
        fillTime("add-check-out", "5:00 PM");
        page.getByTestId("add-save").click();

        assertThat(page.getByTestId("add-save")).hasCount(0);
        assertThat(page.locator(".time-message")).containsText("Time entry added.");
        assertThat(page.locator("[data-date='2026-09-10']")).containsText("8:00 AM");
        assertThat(page.locator("[data-date='2026-09-10']")).containsText("Total 8h 0m, break 1h 0m");
        assertThat(page.getByTestId("month-total")).containsText("Total hours: 8h 0m");
        assertNoHorizontalOverflow();
        screenshot("month-added-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theClockIconOpensAListOfTimesAndAnyMinuteCanStillBeTyped(Viewport viewport) {
        openTimesheet(viewport);
        dayButton("2026-09-10").click();

        // The clock icon opens the list. It shows the times from midnight on in steps of 15 minutes (the list is
        // scrolled, so only the first entries are in the page); the times contain a narrow no-break space.
        page.locator("[data-testid=add-check-in] [part~=toggle-button]").click();
        Locator one = timeItem("1:00\\sAM");
        one.waitFor();
        screenshot("time-list-" + viewport.name());
        one.click();
        assertThat(page.locator("[data-testid=add-check-in] input")).hasValue(Pattern.compile("1:00\\sAM"));

        page.locator("[data-testid=add-check-out] [part~=toggle-button]").click();
        timeItem("3:00\\sAM").click();
        assertThat(page.locator("[data-testid=add-check-out] input")).hasValue(Pattern.compile("3:00\\sAM"));

        fillTime("add-check-in", "1:03 AM"); // typing still works, and the minute need not be a multiple of 15
        page.getByTestId("add-save").click();

        assertThat(page.getByTestId("add-save")).hasCount(0);
        assertThat(page.locator("[data-date='2026-09-10']")).containsText("1:03 AM");
        assertThat(page.locator("[data-date='2026-09-10']")).containsText("3:00 AM");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void aSubmittedMonthOffersNoAddButtonsAndSaysWhy(Viewport viewport) {
        long id = timesheetService.getOrCreate(alice, YearMonth.of(2026, 9)).getId();
        jdbc.update("update timesheet set status = 'SUBMITTED', submitted_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-10-02T10:00:00Z")), id);
        openTimesheet(viewport);

        assertThat(page.getByTestId("add-time-entry")).isDisabled();
        assertThat(page.getByTestId("add-day-entry")).hasCount(0);
        assertThat(page.locator(".timesheet-status")).containsText("cannot be edited because it has been submitted");
        assertReadable(".status-note");
        assertNoHorizontalOverflow();
        screenshot("locked-" + viewport.name());
    }
}
