package com.stubu.specdriven.usecases.uc006_submit_timesheet;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import com.stubu.specdriven.timetracking.TimeEntryService;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-006 Submit Timesheet in a real browser: the button, its explanation while the month is running, the
 * confirmation dialog and the result at the supported screen sizes. The behaviour behind it is covered by
 * {@code UC006SubmitTimesheet}. It is 2026-10-02 09:00 UTC (browser and server), so September can be submitted.
 */
class UC006SubmitTimesheetE2E extends E2ETest {

    private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");

    @Autowired
    TimeEntryService service;
    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    /** Alice, who reports to Bob, worked on two days of September and one day of October. */
    @BeforeEach
    void recordSomeDays() {
        jdbc.update("delete from timesheet");
        Employee bob = employee("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, true);
        Employee alice = employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true);
        jdbc.update("update employee set manager_id = ? where id = ?", bob.getId(), alice.getId());
        record(alice.getId(), "2026-09-01T08:00:00Z", "2026-09-01T12:00:00Z");
        record(alice.getId(), "2026-09-02T09:00:00Z", "2026-09-02T10:30:00Z");
        record(alice.getId(), "2026-10-01T08:00:00Z", "2026-10-01T09:00:00Z");
        clock.set(NOW);
    }

    private void record(long employeeId, String checkIn, String checkOut) {
        clock.set(Instant.parse(checkOut));
        service.checkOutWithMissingCheckIn(employeeId, Instant.parse(checkIn));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void aFinishedMonthIsSubmittedAfterConfirmation(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        page.navigate(url("/timesheet?month=2026-09"));
        page.locator(".day-row").first().waitFor();
        assertThat(page.getByTestId("submit-timesheet")).isEnabled();
        assertReadable(".timesheet-status");
        screenshot("submit-available-" + viewport.name());

        page.getByTestId("submit-timesheet").click();

        assertThat(page.getByText("Submit this timesheet for approval? Once submitted, you cannot make changes "
                + "until it is approved or rejected.")).isVisible();
        assertInsideViewport("[data-testid=submit-confirm]", viewport);
        assertInsideViewport("[data-testid=submit-cancel]", viewport);
        assertReadable(".time-dialog-text");
        screenshot("submit-dialog-" + viewport.name());

        page.getByTestId("submit-confirm").click();

        assertThat(page.locator(".time-message")).hasText("Timesheet submitted successfully. Awaiting manager approval.");
        assertThat(page.getByTestId("status-text")).hasText("Submitted on Oct 2, 2026. Awaiting approval.");
        assertThat(page.getByTestId("timesheet-status")).hasText("Submitted");
        assertThat(page.getByTestId("submit-timesheet")).isHidden();
        assertThat(page.getByTestId("edit-entry")).hasCount(0);
        assertReadable(".time-message");
        assertReadable(".status-note");
        assertNoHorizontalOverflow();
        screenshot("submitted-" + viewport.name());
    }

    @Test
    void cancellingTheConfirmationKeepsTheDraft() {
        open(DESKTOP);
        signInAsAlice();
        page.navigate(url("/timesheet?month=2026-09"));
        page.locator(".day-row").first().waitFor();

        page.getByTestId("submit-timesheet").click();
        page.getByTestId("submit-cancel").click();

        assertThat(page.getByTestId("submit-confirm")).hasCount(0);
        assertThat(page.getByTestId("timesheet-status")).hasText("Not submitted");
        assertThat(page.getByTestId("submit-timesheet")).isEnabled();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theCurrentMonthExplainsWhenItCanBeSubmitted(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        page.navigate(url("/timesheet"));
        page.locator(".day-row").first().waitFor();

        assertThat(page.getByTestId("submit-timesheet")).isDisabled();
        assertThat(page.locator(".status-note")).containsText("once the month has ended, from Nov 1, 2026");
        assertReadable(".status-note");
        assertNoHorizontalOverflow();
        screenshot("submit-not-yet-" + viewport.name());
    }

    @Test
    void aMonthWithoutEntriesShowsTheReasonInsteadOfAConfirmation() {
        open(MOBILE);
        signInAsAlice();
        page.navigate(url("/timesheet?month=2026-08"));
        page.locator(".day-row").first().waitFor();

        page.getByTestId("submit-timesheet").click();

        assertThat(page.locator(".time-message-error"))
                .hasText("Cannot submit an empty timesheet. Add at least one time entry.");
        assertReadable(".time-message-error");
        assertThat(page.getByTestId("submit-confirm")).hasCount(0);
        assertNoHorizontalOverflow();
        screenshot("submit-empty-mobile");
    }

    private void assertInsideViewport(String selector, Viewport viewport) {
        double[] box = box(selector);
        assertTrue(box[0] >= 0 && box[0] + box[2] <= viewport.width() && box[1] >= 0
                && box[1] + box[3] <= viewport.height(), selector + " must be fully visible: "
                + java.util.Arrays.toString(box));
    }
}
