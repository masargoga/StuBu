package com.stubu.specdriven.usecases.uc002_record_check_in_check_out;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import com.stubu.specdriven.testsupport.E2ETest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * UC-002 Record Check-In and Check-Out in a real browser: buttons, messages, dialogs and the timeline as the
 * employee sees them. The behaviour behind it is covered by {@code UC002RecordCheckInCheckOut}. The server
 * clock is controlled by the test (it starts on 2026-09-23 08:03:14 UTC, the browser also reports UTC).
 */
class UC002RecordCheckInCheckOutE2E extends E2ETest {

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    @Test
    void checkingInAndOut_updatesStatusMessagesAndButtonEmphasis() {
        open(DESKTOP);
        signInAsAlice();
        Locator checkIn = page.getByTestId("check-in");
        Locator checkOut = page.getByTestId("check-out");
        Locator message = page.locator(".time-message");

        assertThat(page.locator(".time-status")).hasText("Not checked in");
        assertThat(checkIn).hasAttribute("theme", "primary"); // Check In is the emphasized action
        assertFalse(checkOut.getAttribute("theme") != null && checkOut.getAttribute("theme").contains("primary"));

        checkIn.click();
        assertThat(message).containsText("Checked in at");
        assertEquals("Checked in at 8:03 AM.", text(message));
        assertEquals("Currently working since 8:03 AM", text(page.locator(".time-status")));
        assertThat(checkOut).hasAttribute("theme", "primary");
        assertThat(page.locator(".timeline-row")).hasCount(1);
        assertThat(page.locator(".timeline-bar-open")).hasCount(1);
        screenshot("checked-in");

        clock.advance(Duration.ofHours(4).plusMinutes(4));
        checkOut.click();
        assertThat(message).containsText("Worked 4h 4m");
        assertEquals("Checked out at 12:07 PM. Worked 4h 4m.", text(message));
        assertEquals("Not checked in", text(page.locator(".time-status")));
        assertThat(page.locator(".timeline-bar-open")).hasCount(0);
        assertThat(page.locator(".time-totals")).containsText("Total hours today: 4h 4m");
        assertThat(page.locator(".time-totals")).containsText("Break: 0h 0m");
        assertThat(checkIn).hasAttribute("theme", "primary");
        screenshot("checked-out");
    }

    @Test
    void timeline_drawsEveryPeriodAsABarOnA24HourScale() {
        open(DESKTOP);
        signInAsAlice();
        // 08:03-12:07, break of one hour, then working since 13:07 (seen 90 minutes later).
        page.getByTestId("check-in").click();
        page.locator(".time-message").waitFor();
        clock.advance(Duration.ofHours(4).plusMinutes(4));
        page.getByTestId("check-out").click();
        assertThat(page.locator(".time-message")).containsText("Worked 4h 4m");
        clock.advance(Duration.ofHours(1));
        page.getByTestId("check-in").click();
        assertThat(page.locator(".time-message")).containsText("Checked in at 1:07 PM");
        clock.advance(Duration.ofMinutes(90));
        page.reload();
        page.locator(".timeline-bar-open").waitFor();

        double[] track = box(".timeline-track");
        List<Locator> bars = page.locator(".timeline-bar").all();
        assertEquals(2, bars.size());
        assertBar(bars.get(0), track, 8 * 60 + 3 + 14 / 60.0, 4 * 60 + 4);
        assertBar(bars.get(1), track, 13 * 60 + 7 + 14 / 60.0, 90);
        assertFalse(bars.get(0).getAttribute("class").contains("timeline-bar-open"), "Completed period: solid");
        assertTrue(bars.get(1).getAttribute("class").contains("timeline-bar-open"), "Open period: dashed");
        assertThat(page.locator(".time-totals")).containsText("Total hours today: 5h 34m"); // 4h04 + 1h30
        assertThat(page.locator(".time-totals")).containsText("Break: 1h 0m");
        screenshot("timeline-two-periods");
    }

    @Test
    void checkingInWhileCheckedIn_asksWhetherToReplaceTheCheckInTime() {
        open(DESKTOP);
        signInAsAlice();
        page.getByTestId("check-in").click();
        assertThat(page.locator(".time-message")).containsText("Checked in at 8:03");
        clock.advance(Duration.ofMinutes(10));

        page.getByTestId("check-in").click();

        Locator question = page.getByText(Pattern.compile("You are already checked in since 8:03\\sAM"));
        assertThat(question).isVisible();
        assertThat(page.getByRole(AriaRole.HEADING, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName("Already checked in"))).isVisible();
        Locator replace = page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName("Replace Check-In"));
        Locator cancel = page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName("Cancel"));
        assertThat(replace).isVisible();
        assertThat(cancel).isVisible();
        screenshot("replace-dialog");

        cancel.click();
        assertThat(question).isHidden();
        assertEquals("Currently working since 8:03 AM", text(page.locator(".time-status")));

        page.getByTestId("check-in").click();
        assertThat(question).isVisible(); // asked again, the previous answer was only "Cancel"
        assertThat(replace).isVisible();
        replace.click();
        assertThat(page.locator(".time-message")).containsText("Check-in replaced");
        assertEquals("Currently working since 8:13 AM", text(page.locator(".time-status")));
    }

    @Test
    void checkingOutWithoutCheckIn_asksWhenTheEmployeeStartedAndValidatesTheAnswer() {
        clock.set(Instant.parse("2026-09-23T17:00:00Z"));
        open(DESKTOP);
        signInAsAlice();

        page.getByTestId("check-out").click();

        assertThat(page.getByText("No open check-in was found. When did you start working?")).isVisible();
        assertThat(page.locator("vaadin-date-picker input")).hasValue("9/23/2026");
        page.getByTestId("missing-confirm").click(); // no time entered
        Locator error = page.locator(".time-dialog-error");
        assertThat(error).isVisible();
        assertThat(error).hasAttribute("role", "alert");
        assertThat(error).containsText("Please enter the date and time you started working.");
        assertReadable(".time-dialog-error");
        screenshot("missing-check-in-validation");

        page.locator("vaadin-time-picker input").fill("08:00");
        page.locator("vaadin-time-picker input").press("Enter");
        page.getByTestId("missing-confirm").click();

        assertThat(page.locator(".time-message")).containsText("Worked 9h 0m");
        assertEquals("Checked out at 5:00 PM. Worked 9h 0m.", text(page.locator(".time-message")));
        assertThat(page.locator(".timeline-row")).hasCount(1);
        assertThat(page.getByText("No open check-in was found")).isHidden();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theTimeTrackingPanelIsLargeReadableAndFitsEveryScreenSize(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        page.getByTestId("check-in").click();
        assertThat(page.locator(".time-message")).containsText("Checked in at");

        assertNoHorizontalOverflow();
        double[] checkIn = box("[data-testid=check-in]");
        double[] checkOut = box("[data-testid=check-out]");
        assertTrue(checkIn[3] >= 64 && checkOut[3] >= 64, "Large buttons: " + checkIn[3] + "px high");
        if (viewport.width() > 640) {
            assertEquals(checkIn[1], checkOut[1], 2.0, "Side by side on wide screens");
        } else {
            assertTrue(checkOut[1] >= checkIn[1] + checkIn[3], "Stacked on narrow screens");
            assertEquals(checkIn[2], checkOut[2], 2.0, "Full width buttons");
        }
        double[] track = box(".timeline-track");
        assertTrue(track[0] >= 0 && track[0] + track[2] <= viewport.width(), "Timeline fits the screen");
        assertNavigationLayout(viewport, ".home-welcome");
        assertReadable(".time-message");
        assertReadable(".time-status");
        assertReadable(".time-date");
        assertReadable(".timeline-label");
        screenshot("panel-" + viewport.name());
    }

    /** The bar starts and is as long as expected, as fractions of the track (a 24 hour day). */
    private static void assertBar(Locator bar, double[] track, double startMinute, double lengthMinutes) {
        var box = bar.boundingBox();
        double left = (box.x - track[0]) / track[2] * 24 * 60;
        double width = box.width / track[2] * 24 * 60;
        assertEquals(startMinute, left, 6.0, "Bar starts at " + startMinute + " minutes into the day");
        assertEquals(lengthMinutes, width, 6.0, "Bar is " + lengthMinutes + " minutes long");
    }
}
