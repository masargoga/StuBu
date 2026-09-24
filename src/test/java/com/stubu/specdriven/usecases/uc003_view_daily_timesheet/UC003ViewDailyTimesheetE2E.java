package com.stubu.specdriven.usecases.uc003_view_daily_timesheet;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.stubu.specdriven.testsupport.E2ETest;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-003 View Daily Timesheet in a real browser: how the day, the current and elapsed time, the status of each
 * entry, the disabled edit control and the empty and error states look and behave. The behaviour behind it is
 * covered by {@code UC003ViewDailyTimesheet}.
 */
class UC003ViewDailyTimesheetE2E extends E2ETest {

    @MockitoSpyBean
    TimeEntryRepository timeEntries;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    @AfterEach
    void repairDatabase() {
        Mockito.reset(timeEntries);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void anEmptyDay_showsAHintAndTheCurrentTimeAtEveryScreenSize(Viewport viewport) {
        open(viewport);
        signInAsAlice();

        assertThat(page.locator(".time-empty")).containsText("No time entries recorded yet");
        assertThat(page.getByTestId("current-time")).hasText(Pattern.compile("Current time: 8:03\\sAM"));
        assertThat(page.getByTestId("total-worked")).hasText("Total hours today: 0h 0m");
        assertThat(page.getByTestId("elapsed-time")).isHidden();
        assertThat(page.locator(".timeline-row")).hasCount(0);
        assertThat(page.locator(".timeline-axis")).isVisible();
        assertNoHorizontalOverflow();
        assertReadable(".time-empty");
        assertReadable(".time-current");
        screenshot("empty-day-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void anOpenEntry_showsStatusElapsedTimeAndTheDisabledEditControlAtEveryScreenSize(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        page.getByTestId("check-in").click();
        assertThat(page.locator(".time-message")).containsText("Checked in at");
        clock.advance(Duration.ofHours(2).plusMinutes(15));
        page.reload();
        page.getByTestId("elapsed-time").waitFor();

        assertThat(page.getByTestId("elapsed-time")).hasText("Elapsed since check-in: 2h 15m");
        assertThat(page.getByTestId("current-time")).hasText(Pattern.compile("Current time: 10:18\\sAM"));
        assertThat(page.getByTestId("total-worked")).hasText("Total hours today: 2h 15m");
        assertThat(page.locator(".time-total-detail")).hasText("0h 0m completed + 2h 15m on the open entry");
        assertThat(page.locator(".time-empty")).isHidden();
        assertThat(page.getByTestId("entry-status")).hasText("In Progress");
        Locator edit = page.getByTestId("edit-entry");
        assertThat(edit).isDisabled();
        assertThat(edit).hasText("Edit");
        assertNoHorizontalOverflow();
        double[] row = box(".timeline-row");
        assertTrue(row[0] >= 0 && row[0] + row[2] <= viewport.width(), "Row fits the screen");
        assertReadable(".time-elapsed");
        assertReadable(".time-total-detail");
        assertReadable(".timeline-label");
        screenshot("open-entry-" + viewport.name());
    }

    @Test
    void theEditControlExplainsWhyItIsDisabled() {
        open(DESKTOP);
        signInAsAlice();
        page.getByTestId("check-in").click();
        assertThat(page.locator(".timeline-row")).hasCount(1);

        page.locator(".timeline-edit").hover();

        assertThat(page.getByRole(AriaRole.TOOLTIP)).containsText("Editing time entries is not available yet.");
        screenshot("edit-tooltip");
    }

    @Test
    void aCompletedEntryShowsItsStatusAndThePanelRefreshesItselfWhileOpen() {
        open(DESKTOP);
        signInAsAlice();
        page.getByTestId("check-in").click();
        assertThat(page.locator(".time-message")).containsText("Checked in at");
        clock.advance(Duration.ofMinutes(30));
        page.getByTestId("check-out").click();
        assertThat(page.getByTestId("entry-status")).hasText("Completed");

        // Time passes on the server; nobody clicks or reloads: the panel updates on its own.
        clock.advance(Duration.ofMinutes(5));

        assertThat(page.getByTestId("current-time")).hasText(Pattern.compile("Current time: 8:38\\sAM"),
                new LocatorAssertions.HasTextOptions().setTimeout(15_000));
        assertEquals("Checked out at 8:33 AM. Worked 0h 30m.", text(page.locator(".time-message")),
                "The message about the last action stays");
        screenshot("completed-entry");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void whenTheDayCannotBeLoaded_anErrorWithRetryReplacesTheTimeline(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        page.getByTestId("check-in").click();
        assertThat(page.locator(".timeline-row")).hasCount(1);
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timeEntries)
                .findStartedBetween(any(), any(), any());
        page.reload();

        Locator error = page.locator(".time-load-error");
        assertThat(error).isVisible();
        assertThat(error).hasAttribute("role", "alert");
        assertThat(error).containsText("Unable to load time entries. Please try again.");
        assertThat(page.getByTestId("retry")).isVisible();
        assertThat(page.locator(".timeline")).isHidden();
        assertThat(page.getByTestId("total-worked")).isHidden();
        assertNoHorizontalOverflow();
        assertReadable(".time-load-error");
        screenshot("load-error-" + viewport.name());

        Mockito.reset(timeEntries);
        page.getByTestId("retry").click();

        assertThat(error).isHidden();
        assertThat(page.locator(".timeline-row")).hasCount(1);
        assertThat(page.getByTestId("total-worked")).isVisible();
    }
}
