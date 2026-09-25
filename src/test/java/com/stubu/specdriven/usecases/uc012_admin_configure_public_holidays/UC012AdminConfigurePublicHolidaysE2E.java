package com.stubu.specdriven.usecases.uc012_admin_configure_public_holidays;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-012 Administrator Configure Public Holidays in a real browser: the list, the form with its date picker, the
 * validation messages, the delete dialog and the effect in the month view at the supported screen sizes. The
 * behaviour behind it is covered by {@code UC012AdminConfigurePublicHolidays}. It is 2026-10-05 (browser and server).
 */
class UC012AdminConfigurePublicHolidaysE2E extends E2ETest {

    private static final String CAROL = "carol.admin@example.com";
    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    @BeforeEach
    void setUpHolidays() {
        employee(CAROL, "Carol", "Admin", Role.ADMIN, true);
        jdbc.update("delete from public_holiday");
        jdbc.update("insert into public_holiday (holiday_date, name, created_at) values ('2026-12-25', 'Christmas', "
                + "current_timestamp)");
        jdbc.update("insert into public_holiday (holiday_date, name, created_at) values ('2026-01-01', "
                + "'New Year''s Day', current_timestamp)");
        clock.set(NOW);
    }

    private void signInAndOpenList(Viewport viewport) {
        open(viewport);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/admin/holidays"));
        page.getByTestId("holidays").waitFor();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theListFitsEveryScreenSize(Viewport viewport) {
        open(viewport);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();
        if (viewport.width() < 1024) {
            page.getByTestId("drawer-toggle").click();
        }
        assertThat(page.getByTestId("nav-holidays")).isVisible();
        page.getByTestId("nav-holidays").click();
        page.getByTestId("holidays").waitFor();

        assertThat(page.getByTestId("holiday-row")).hasCount(2);
        assertThat(page.getByTestId("holidays")).containsText("Christmas");
        assertThat(page.getByTestId("holidays")).containsText("New Year's Day");
        assertThat(page.getByTestId("add-holiday")).isVisible();
        assertReadable(".approval-employee");
        assertNoHorizontalOverflow();
        screenshot("list-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void aHolidayIsAddedWithTheDatePicker(Viewport viewport) {
        signInAndOpenList(viewport);
        page.getByTestId("add-holiday").click();
        page.getByTestId("holiday-save").waitFor();
        assertInsideViewport("[data-testid=holiday-save]", viewport);
        assertInsideViewport("[data-testid=holiday-cancel]", viewport);
        screenshot("form-" + viewport.name());

        page.getByTestId("holiday-save").click(); // nothing filled in yet

        assertThat(page.getByText("Please enter a valid date.")).isVisible();
        assertThat(page.getByText("Please enter a holiday name.")).isVisible();
        screenshot("form-errors-" + viewport.name());

        enterDate(viewport, "12/25/2026", "2026-12-25"); // already taken
        page.locator("[data-testid=holiday-name] input").fill("Second Christmas");
        page.getByTestId("holiday-save").click();
        assertThat(page.getByText("A holiday is already configured for")).isVisible();

        enterDate(viewport, "12/26/2026", "2026-12-26");
        page.getByTestId("holiday-save").click();

        assertThat(page.locator(".time-message")).hasText("Holiday Second Christmas added on Dec 26, 2026.");
        assertThat(page.getByTestId("holiday-row")).hasCount(3);
        assertReadable(".time-message");
        assertNoHorizontalOverflow();
        screenshot("added-" + viewport.name());
    }

    @Test
    void aHolidayCanBeEditedAndDeleted() {
        signInAndOpenList(DESKTOP);
        var christmas = page.getByTestId("holiday-row").filter(new com.microsoft.playwright.Locator.FilterOptions()
                .setHasText("Christmas"));

        christmas.getByTestId("edit-holiday").click();
        page.locator("[data-testid=holiday-name] input").fill("Christmas Day");
        page.getByTestId("holiday-save").click();
        assertThat(page.locator(".time-message")).hasText("Holiday updated.");
        assertThat(page.getByTestId("holidays")).containsText("Christmas Day");

        page.getByTestId("holiday-row").filter(new com.microsoft.playwright.Locator.FilterOptions()
                .setHasText("Christmas Day")).getByTestId("delete-holiday").click();
        assertThat(page.getByText("Delete Christmas Day on Dec 25, 2026? This cannot be undone.")).isVisible();
        assertInsideViewport("[data-testid=delete-confirm]", DESKTOP);
        screenshot("delete-dialog");
        page.getByTestId("delete-confirm").click();

        assertThat(page.locator(".time-message")).hasText("Holiday deleted.");
        assertThat(page.getByTestId("holiday-row")).hasCount(1);
    }

    @Test
    void aConfiguredHolidayIsMarkedInTheMonthView() {
        jdbc.update("insert into public_holiday (holiday_date, name, created_at) values ('2026-09-15', "
                + "'Founders Day', current_timestamp)");
        open(DESKTOP);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();

        page.navigate(url("/timesheet?month=2026-09"));
        page.locator(".day-row").first().waitFor();

        assertThat(page.getByTestId("holiday")).hasText("Public holiday: Founders Day");
        assertThat(page.locator(".day-holiday")).hasCount(1);
    }

    /**
     * Enters a date. Wide screens type it like a user; on a phone the date picker takes over the whole screen
     * with its own calendar, so the value is set directly and the change reported as the picker itself does.
     */
    private void enterDate(Viewport viewport, String typed, String iso) {
        if (viewport.width() >= 768) {
            page.locator("[data-testid=holiday-date] input").fill(typed);
            page.locator("[data-testid=holiday-date] input").press("Enter");
        } else {
            page.evaluate("iso => { const picker = document.querySelector('[data-testid=holiday-date]'); picker.value = iso; "
                    + "picker.dispatchEvent(new CustomEvent('change', { bubbles: true })); }", iso);
        }
    }

    private void assertInsideViewport(String selector, Viewport viewport) {
        double[] box = box(selector);
        assertTrue(box[0] >= 0 && box[0] + box[2] <= viewport.width() && box[1] >= 0
                && box[1] + box[3] <= viewport.height(), selector + " must be fully visible: "
                + java.util.Arrays.toString(box));
    }
}
