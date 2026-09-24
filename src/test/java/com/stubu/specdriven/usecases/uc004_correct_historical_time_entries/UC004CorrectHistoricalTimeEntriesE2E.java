package com.stubu.specdriven.usecases.uc004_correct_historical_time_entries;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.stubu.specdriven.testsupport.E2ETest;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.TimeEntryService;
import java.time.Instant;
import java.time.YearMonth;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-004 Correct Historical Time Entries in a real browser: how the edit and delete dialogs, their messages and
 * the locked state look and behave at the supported screen sizes. The behaviour behind it is covered by
 * {@code UC004CorrectHistoricalTimeEntries}. It is 2026-09-23 17:00 UTC (browser and server).
 */
class UC004CorrectHistoricalTimeEntriesE2E extends E2ETest {

    private static final Instant NOW = Instant.parse("2026-09-23T17:00:00Z");

    @Autowired
    TimeEntryService service;
    @Autowired
    TimesheetRepository timesheets;
    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    /** Alice worked 08:00-12:00 and 13:00-15:00 today. */
    @BeforeEach
    void recordTwoPeriods() {
        jdbc.update("delete from timesheet");
        long alice = employee(ALICE, "Alice", "Employee", com.stubu.specdriven.employee.Role.EMPLOYEE, true).getId();
        clock.set(Instant.parse("2026-09-23T12:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T08:00:00Z"));
        clock.set(Instant.parse("2026-09-23T15:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T13:00:00Z"));
        clock.set(NOW);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theEditDialogFitsTheScreenAndShowsValidationErrorsReadably(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        assertThat(page.locator(".timeline-row")).hasCount(2);

        page.getByTestId("edit-entry").first().click();

        assertThat(page.getByText("Edit time entry", new com.microsoft.playwright.Page.GetByTextOptions()
                .setExact(true))).isVisible();
        assertThat(page.locator("vaadin-date-picker input").first()).hasValue("9/23/2026");
        assertThat(page.locator("vaadin-time-picker input").first()).hasValue(java.util.regex.Pattern.compile(
                "8:00\\sAM"));
        assertThat(page.locator("vaadin-date-picker").first()).hasAttribute("readonly", "");
        assertInsideViewport("[data-testid=edit-save]", viewport);
        assertInsideViewport("[data-testid=edit-cancel]", viewport);
        var checkInDate = page.locator("vaadin-date-picker").first().boundingBox();
        var checkInTime = page.locator("vaadin-time-picker").first().boundingBox();
        if (viewport.width() > 640) {
            assertEquals(checkInDate.y, checkInTime.y, 2.0, "Date and time side by side in a wide dialog");
        } else {
            assertTrue(checkInTime.y > checkInDate.y, "Stacked in a narrow dialog");
        }

        page.locator("vaadin-time-picker input").nth(1).fill("07:00"); // check-out before the check-in
        page.locator("vaadin-time-picker input").nth(1).press("Enter");
        page.getByTestId("edit-save").click();

        Locator error = page.locator(".time-dialog-error");
        assertThat(error).isVisible();
        assertThat(error).hasAttribute("role", "alert");
        assertThat(error).containsText("Check-out time must be after check-in time.");
        assertReadable(".time-dialog-error");
        assertNoHorizontalOverflow();
        screenshot("edit-dialog-error-" + viewport.name());
    }

    @Test
    void correctingAnEntryThroughTheDialogUpdatesTheTimelineAndTheTotals() {
        open(DESKTOP);
        signInAsAlice();
        page.getByTestId("edit-entry").first().click();

        page.locator("vaadin-time-picker input").nth(0).fill("08:30");
        page.locator("vaadin-time-picker input").nth(0).press("Enter");
        page.locator("vaadin-time-picker input").nth(1).fill("12:15");
        page.locator("vaadin-time-picker input").nth(1).press("Enter");
        page.getByLabel("Reason for the correction (optional)").fill("Forgot to check in on time");
        screenshot("edit-dialog-filled");
        page.getByTestId("edit-save").click();

        assertEquals("Time entry updated.", text(page.locator(".time-message")));
        assertThat(page.locator(".timeline-label").first()).hasText(java.util.regex.Pattern.compile(
                "8:30\\sAM.*12:15\\sPM, 3h 45m"));
        assertThat(page.getByTestId("total-worked")).hasText("Total hours today: 5h 45m"); // 3h45 + 2h
        assertThat(page.getByText("Edit time entry", new com.microsoft.playwright.Page.GetByTextOptions()
                .setExact(true))).isHidden();
        screenshot("edit-done");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void deletingAnEntryAsksForConfirmationAndRemovesItFromTheTimeline(Viewport viewport) {
        open(viewport);
        signInAsAlice();

        page.getByTestId("delete-entry").first().click();

        assertThat(page.getByText("Are you sure you want to delete this time entry? This cannot be undone."))
                .isVisible();
        assertInsideViewport("[data-testid=delete-confirm]", viewport);
        assertInsideViewport("[data-testid=delete-cancel]", viewport);
        screenshot("delete-dialog-" + viewport.name());

        page.getByLabel("Reason for the correction (optional)").fill("Entered twice");
        page.getByTestId("delete-confirm").click();

        assertEquals("Time entry deleted.", text(page.locator(".time-message")));
        assertThat(page.locator(".timeline-row")).hasCount(1);
        assertThat(page.getByTestId("total-worked")).hasText("Total hours today: 2h 0m");
        assertNoHorizontalOverflow();
    }

    @Test
    void cancellingTheDeleteDialogKeepsTheEntry() {
        open(DESKTOP);
        signInAsAlice();
        page.getByTestId("delete-entry").first().click();

        page.getByTestId("delete-cancel").click();

        assertThat(page.getByTestId("delete-confirm")).hasCount(0);
        assertThat(page.locator(".timeline-row")).hasCount(2);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void anEntryOfASubmittedTimesheetIsLockedWithAnExplanationInItsRow(Viewport viewport) {
        long alice = employee(ALICE, "Alice", "Employee", com.stubu.specdriven.employee.Role.EMPLOYEE, true).getId();
        timesheets.save(new Timesheet(alice, YearMonth.of(2026, 9), TimesheetStatus.SUBMITTED));
        open(viewport);

        signInAsAlice();

        assertThat(page.locator(".timeline-row")).hasCount(2);
        assertThat(page.getByTestId("entry-locked")).hasCount(2);
        assertThat(page.getByTestId("entry-locked").first()).containsText("This entry cannot be edited because the "
                + "timesheet has been submitted. Wait for approval or rejection before making changes.");
        assertThat(page.getByTestId("edit-entry").first()).isDisabled();
        assertThat(page.getByTestId("delete-entry").first()).isDisabled();
        assertReadable(".timeline-locked");
        assertNoHorizontalOverflow();
        double[] row = box(".timeline-row");
        assertTrue(row[0] >= 0 && row[0] + row[2] <= viewport.width(), "Row fits the screen");
        screenshot("locked-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theEditAndDeleteControlsAreUsableAtEveryScreenSize(Viewport viewport) {
        open(viewport);
        signInAsAlice();

        assertNoHorizontalOverflow();
        for (Locator control : page.locator("[data-testid=edit-entry], [data-testid=delete-entry]").all()) {
            var box = control.boundingBox();
            assertTrue(box.x >= 0 && box.x + box.width <= viewport.width(), "Control fits the screen");
            assertTrue(box.height >= 28, "Reachable control, but only " + box.height + "px high");
        }
        screenshot("row-controls-" + viewport.name());
    }

    private void assertInsideViewport(String selector, Viewport viewport) {
        double[] box = box(selector);
        assertTrue(box[0] >= 0 && box[0] + box[2] <= viewport.width() && box[1] >= 0
                && box[1] + box[3] <= viewport.height(), selector + " must be fully visible: " + java.util.Arrays
                .toString(box));
    }
}
