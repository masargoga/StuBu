package com.stubu.specdriven.usecases.uc017_regional_public_holidays;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-017 Regional Public Holidays in a real browser: the region selector and the region dialog on the holidays page,
 * the region in the employee form and the region note in the month view, at the supported screen sizes. The
 * behaviour behind it is covered by {@code UC017RegionalPublicHolidays}. It is 2026-09-23 (browser and server).
 */
class UC017RegionalPublicHolidaysE2E extends E2ETest {

    private static final String CAROL = "carol.admin@example.com";
    private static final Instant NOW = Instant.parse("2026-09-23T08:03:14Z");

    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    @BeforeEach
    void setUpRegions() {
        clock.set(NOW);
        jdbc.update("delete from employee_setting"); // another test class may have chosen a language
        employee(CAROL, "Carol", "Admin", Role.ADMIN, true);
        reset();
        long bavaria = region("Germany – Bavaria");
        long usa = region("USA");
        holiday(1, "2026-09-15", "Founders Day");
        holiday(bavaria, "2026-09-20", "Bavarian Day");
        holiday(usa, "2026-09-07", "Labor Day");
        holiday(usa, "2026-11-26", "Thanksgiving");
        jdbc.update("update employee set region_id = ? where email = ?", usa, ALICE);
    }

    @AfterEach
    void restore() {
        reset();
    }

    private void reset() {
        jdbc.update("delete from public_holiday");
        jdbc.update("update employee set region_id = 1");
        jdbc.update("delete from region where id <> 1");
    }

    private long region(String name) {
        jdbc.update("insert into region (name, version, created_at, updated_at) values (?, 0, current_timestamp, "
                + "current_timestamp)", name);
        return jdbc.queryForObject("select id from region where name = ?", Long.class, name);
    }

    private void holiday(long regionId, String date, String name) {
        jdbc.update("insert into public_holiday (region_id, holiday_date, name, created_at) values (?, ?, ?, "
                + "current_timestamp)", regionId, java.sql.Date.valueOf(date), name);
    }

    private void signInAndOpenHolidays(Viewport viewport) {
        open(viewport);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/admin/holidays"));
        page.getByTestId("holiday-region").waitFor();
    }

    private void choose(String testId, String text) {
        page.getByTestId(testId).click();
        page.locator("vaadin-select-list-box vaadin-select-item").filter(new Locator.FilterOptions().setHasText(text))
                .first().click();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theRegionSelectorShowsTheHolidaysOfTheChosenRegionAtEveryScreenSize(Viewport viewport) {
        signInAndOpenHolidays(viewport);

        assertThat(page.getByTestId("holiday-region")).isVisible();
        assertThat(page.getByTestId("manage-regions")).isVisible();
        assertThat(page.getByTestId("holiday-row")).hasCount(1);
        assertThat(page.getByTestId("holidays")).containsText("Founders Day");
        assertNoHorizontalOverflow();
        screenshot("holidays-default-" + viewport.name());

        choose("holiday-region", "USA");

        assertThat(page.getByTestId("holiday-row")).hasCount(1);
        assertThat(page.getByTestId("holidays")).containsText("Labor Day");
        assertNoHorizontalOverflow();
        assertReadable(".approval-employee");
        screenshot("holidays-usa-" + viewport.name());

        page.getByTestId("add-holiday").click();
        page.getByTestId("holiday-save").waitFor();
        assertThat(page.locator("[data-testid=holiday-region-shown] input")).hasValue("USA");
        assertInsideViewport("[data-testid=holiday-save]", viewport);
        screenshot("holiday-form-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theRegionDialogAddsARegionAndExplainsWhyOneCannotBeDeleted(Viewport viewport) {
        signInAndOpenHolidays(viewport);
        page.getByTestId("manage-regions").click();
        page.getByTestId("region-add").waitFor();

        assertThat(page.getByTestId("region-row")).hasCount(3);
        assertInsideViewport("[data-testid=region-close]", viewport);
        assertInsideViewport("[data-testid=region-add]", viewport);
        screenshot("regions-" + viewport.name());

        page.locator("[data-testid=region-name-new] input").fill("Spain");
        page.getByTestId("region-add").click();

        assertThat(page.getByTestId("region-message")).containsText("Region Spain added.");
        assertThat(page.getByTestId("region-row")).hasCount(4);
        assertReadable("[data-testid=region-message]");

        page.getByTestId("region-row").filter(new Locator.FilterOptions().setHasText("USA"))
                .getByTestId("region-delete").click();
        page.getByTestId("region-delete-confirm").click();

        assertThat(page.getByTestId("region-message")).containsText("This region cannot be deleted");
        assertReadable("[data-testid=region-message]");
        screenshot("regions-in-use-" + viewport.name());

        page.getByTestId("region-close").click();
        assertThat(page.getByTestId("region-add")).not().isVisible();
        page.getByTestId("holiday-region").click();
        assertThat(page.locator("vaadin-select-list-box vaadin-select-item").filter(new Locator.FilterOptions()
                .setHasText("Spain"))).hasCount(1); // the selector of the page knows the new region
        page.keyboard().press("Escape");
        assertNoHorizontalOverflow();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theEmployeeFormHasTheRegion(Viewport viewport) {
        open(viewport);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/admin/employees"));
        page.getByTestId("add-employee").click();
        page.getByTestId("employee-region").waitFor();

        assertThat(page.getByTestId("employee-region")).isVisible();
        assertThat(page.getByTestId("employee-region")).containsText("Default");
        assertInsideViewport("[data-testid=employee-save]", viewport);
        screenshot("employee-form-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theMonthViewNamesTheRegionAndMarksItsHolidays(Viewport viewport) {
        open(viewport);
        signInAsAlice();
        page.navigate(url("/timesheet"));
        page.getByTestId("holiday-region-note").waitFor();

        assertThat(page.getByTestId("holiday-region-note")).hasText("Public holidays: USA");
        assertThat(page.getByTestId("holiday").first()).containsText("Labor Day");
        assertThat(page.getByTestId("holiday")).hasCount(1);
        assertReadable(".holiday-region-note");
        assertNoHorizontalOverflow();
        screenshot("month-" + viewport.name());
    }

    private void assertInsideViewport(String selector, Viewport viewport) {
        double[] box = box(selector);
        assertTrue(box[0] >= 0 && box[0] + box[2] <= viewport.width() && box[1] >= 0
                && box[1] + box[3] <= viewport.height(), selector + " is inside the window: " + java.util.Arrays.toString(box));
    }
}
