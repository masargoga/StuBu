package com.stubu.specdriven.usecases.uc003_view_daily_timesheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.home.HomeView;
import com.stubu.specdriven.security.LoginView;
import com.stubu.specdriven.testsupport.MutableClock;
import com.stubu.specdriven.testsupport.TestClockConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.stubu.specdriven.timetracking.DaySummary;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.stubu.specdriven.timetracking.TimeTrackingPanel;
import com.stubu.specdriven.timetracking.WorkTimeline;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Element;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-003 View Daily Timesheet. The day is shown by the time tracking panel on the home page; the test clock
 * decides what "now" is, and work periods are recorded through the service like the employee would.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@WithEmployee
class UC003ViewDailyTimesheet extends SpringBrowserlessTest {

    private static final Instant START = TestClockConfiguration.START; // 2026-09-23 08:03:14Z

    @Autowired
    MutableClock clock;
    @Autowired
    TimeEntryService service;
    @MockitoSpyBean
    TimeEntryRepository timeEntries;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    JdbcTemplate jdbc;

    private long alice;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(timeEntries);
        clock.set(START);
        jdbc.update("delete from time_entry");
        alice = employees.findByEmailIgnoreCase("alice.employee@example.com").orElseThrow().getId();
    }

    @AfterEach
    void resetRepository() {
        Mockito.reset(timeEntries);
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_completedAndOpenEntriesShowTimesDurationAndStatus() {
        clock.set(Instant.parse("2026-09-23T10:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T08:00:00Z")); // 08:00-10:00
        clock.set(Instant.parse("2026-09-23T11:00:00Z"));
        service.checkIn(alice);
        clock.set(Instant.parse("2026-09-23T12:15:00Z"));

        TimeTrackingPanel panel = openPanel();

        assertEquals(List.of("8:00 AM – 10:00 AM, 2h 0m", "11:00 AM – in progress, 1h 15m so far"),
                texts(panel, "timeline-label"));
        assertEquals(List.of("Completed", "In Progress"), badges(panel));
    }

    @Test
    void mainFlow_currentTimeElapsedTimeAndTotalsAreShown() {
        service.checkIn(alice); // 08:03:14
        clock.advance(Duration.ofHours(2).plusMinutes(15)); // 10:18:14

        TimeTrackingPanel panel = openPanel();

        assertTrue(text(panel).contains("Current time: 10:18 AM"), text(panel));
        assertTrue(text(panel).contains("Elapsed since check-in: 2h 15m"), text(panel));
        assertTrue(text(panel).contains("Total hours today: 2h 15m"), text(panel));
        assertTrue(text(panel).contains("Currently working since 8:03 AM"), text(panel));
        assertTrue(text(panel).contains("Break: 0h 0m"), text(panel));
        assertTrue(text(panel).contains("Wednesday, September 23, 2026"), text(panel));
    }

    @Test
    void mainFlow_theCheckInAndCheckOutButtonsAreShown() {
        TimeTrackingPanel panel = openPanel();

        assertTrue(button(panel, "check-in").isEnabled());
        assertTrue(button(panel, "check-out").isEnabled());
        assertTrue(button(panel, "check-in").hasThemeName("primary"));
    }

    @Test
    void mainFlow_everyEntryHasEditAndDeleteControls() {
        clock.set(Instant.parse("2026-09-23T10:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T08:00:00Z"));
        service.checkIn(alice);

        TimeTrackingPanel panel = openPanel();

        List<Button> edits = buttons(panel, "edit-entry");
        List<Button> deletes = buttons(panel, "delete-entry");
        assertEquals(2, edits.size(), "One edit control per entry");
        assertEquals(2, deletes.size(), "One delete control per entry");
        assertTrue(edits.stream().allMatch(Button::isEnabled), "Correcting entries is UC-004");
        assertTrue(edits.stream().allMatch(edit -> "Edit".equals(edit.getText())));
        assertTrue(deletes.stream().allMatch(delete -> "Delete".equals(delete.getText())));
        assertTrue(normalize(edits.getFirst().getElement().getAttribute("aria-label")).contains("8:00 AM"));
    }

    @Test
    void mainFlow_theDayRefreshesWhenTheTimePasses() {
        service.checkIn(alice);
        TimeTrackingPanel panel = openPanel();
        assertTrue(text(panel).contains("Current time: 8:03 AM"), text(panel));
        assertTrue(text(panel).contains("Total hours today: 0h 0m"), text(panel));

        clock.advance(Duration.ofMinutes(31)); // what the automatic refresh does every few seconds
        panel.refresh();

        assertTrue(text(panel).contains("Current time: 8:34 AM"), text(panel));
        assertTrue(text(panel).contains("Elapsed since check-in: 0h 31m"), text(panel));
        assertTrue(text(panel).contains("Total hours today: 0h 31m"), text(panel));
    }

    // --- AF-1: No Time Entries Today -------------------------------------------------------------

    @Test
    void af1_noEntries_showsAnEmptyTimelineAndAHint() {
        TimeTrackingPanel panel = openPanel();

        assertEquals(0, timelineRows(panel).size());
        assertEquals(1, find(WorkTimeline.class).all().size(), "The (empty) timeline is still there");
        assertTrue(text(panel).contains("Total hours today: 0h 0m"), text(panel));
        assertTrue(text(panel).contains("No time entries recorded yet. Click 'Check In' to start."), text(panel));
        assertTrue(button(panel, "check-in").hasThemeName("primary"));
        assertTrue(button(panel, "check-out").isEnabled());
        assertTrue(text(panel).contains("Not checked in"), text(panel));
    }

    @Test
    void af1_theHintDisappearsOnceThereIsAnEntry() {
        service.checkIn(alice);

        TimeTrackingPanel panel = openPanel();

        assertFalse(text(panel).contains("No time entries recorded yet"), text(panel));
    }

    // --- AF-2: Open Entry ------------------------------------------------------------------------

    @Test
    void af2_openEntry_totalIsCompletedPlusElapsedWithTheBreakdown() {
        clock.set(Instant.parse("2026-09-23T10:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T08:00:00Z")); // 2h completed
        clock.set(Instant.parse("2026-09-23T11:00:00Z"));
        service.checkIn(alice);
        clock.set(Instant.parse("2026-09-23T11:45:00Z")); // 45m elapsed

        TimeTrackingPanel panel = openPanel();

        assertTrue(text(panel).contains("Total hours today: 2h 45m"), text(panel));
        assertTrue(text(panel).contains("2h 0m completed + 0h 45m on the open entry"), text(panel));
        assertTrue(text(panel).contains("Break: 1h 0m"), text(panel));
    }

    @Test
    void af2_withoutAnOpenEntryThereIsNoBreakdown() {
        clock.set(Instant.parse("2026-09-23T10:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T08:00:00Z"));

        TimeTrackingPanel panel = openPanel();

        assertFalse(text(panel).contains("on the open entry"), text(panel));
        assertFalse(text(panel).contains("Elapsed since check-in"), text(panel));
    }

    // --- AF-3: Database Error Loading Entries ----------------------------------------------------

    @Test
    void af3_databaseErrorWhileLoading_showsAnErrorWithRetryAndNoTimelineOrTotals() {
        service.checkIn(alice);
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timeEntries)
                .findStartedBetween(any(), any(), any());

        TimeTrackingPanel panel = openPanel();

        assertTrue(text(panel).contains("Unable to load time entries. Please try again."), text(panel));
        assertTrue(button(panel, "retry").isVisible());
        assertEquals(0, find(WorkTimeline.class).all().size(), "No timeline is shown");
        assertFalse(text(panel).contains("Total hours today"), text(panel));
        assertFalse(text(panel).contains("Currently working"), text(panel));
    }

    @Test
    void af3_retryLoadsTheDayOnceTheDatabaseIsBack() {
        service.checkIn(alice);
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(timeEntries)
                .findStartedBetween(any(), any(), any());
        TimeTrackingPanel panel = openPanel();
        Mockito.reset(timeEntries);

        test(button(panel, "retry")).click();

        assertFalse(text(panel).contains("Unable to load time entries"), text(panel));
        assertTrue(text(panel).contains("Currently working since 8:03 AM"), text(panel));
        assertEquals(1, timelineRows(panel).size());
        assertTrue(text(panel).contains("Total hours today: 0h 0m"), text(panel));
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_onlyTodaysEntriesAreShown() {
        clock.set(Instant.parse("2026-09-22T12:00:00Z")); // yesterday 10:00-12:00
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-22T10:00:00Z"));
        clock.set(Instant.parse("2026-09-23T12:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T09:00:00Z")); // today 09:00-12:00

        TimeTrackingPanel panel = openPanel();

        assertEquals(List.of("9:00 AM – 12:00 PM, 3h 0m"), texts(panel, "timeline-label"));
        assertTrue(text(panel).contains("Total hours today: 3h 0m"), text(panel));
    }

    @Test
    void br01_todayIsTheUsersDayInTheirTimeZone() {
        clock.set(Instant.parse("2026-09-23T02:00:00Z"));
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-22T23:30:00Z"));

        DaySummary utc = service.today(alice, ZoneOffset.UTC);
        DaySummary berlin = service.today(alice, ZoneId.of("Europe/Berlin")); // 01:30 local, still on the 23rd

        assertEquals(0, utc.entries().size(), "23:30Z on the 22nd is yesterday in UTC");
        assertEquals(1, berlin.entries().size(), "...but 01:30 today in Berlin");
    }

    @Test
    void br01_anOpenEntryFromBeforeMidnightIsStillShownSoItCanBeEnded() {
        clock.set(Instant.parse("2026-09-22T22:00:00Z"));
        service.checkIn(alice);
        clock.set(Instant.parse("2026-09-23T06:00:00Z"));

        TimeTrackingPanel panel = openPanel();

        assertEquals(1, timelineRows(panel).size());
        assertEquals(List.of("In Progress"), badges(panel));
        assertTrue(text(panel).contains("Elapsed since check-in: 8h 0m"), text(panel));
    }

    @Test
    void br02_entriesAreInChronologicalOrder() {
        clock.set(Instant.parse("2026-09-23T15:00:00Z"));
        // Recorded latest first: 13:00-15:00, then the earlier 09:00-10:00 (entered afterwards, in the past).
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-09-23T13:00:00Z"));
        clock.set(Instant.parse("2026-09-23T15:00:00Z"));
        Instant earlier = Instant.parse("2026-09-23T09:00:00Z");
        jdbc.update("insert into time_entry (employee_id, check_in_at, check_out_at, version, created_at, updated_at)"
                + " values (?, ?, ?, 0, current_timestamp, current_timestamp)", alice,
                java.sql.Timestamp.from(earlier), java.sql.Timestamp.from(earlier.plusSeconds(3600)));

        TimeTrackingPanel panel = openPanel();

        assertEquals(List.of("9:00 AM – 10:00 AM, 1h 0m", "1:00 PM – 3:00 PM, 2h 0m"),
                texts(panel, "timeline-label"));
        assertTrue(text(panel).contains("Break: 3h 0m"), text(panel));
    }

    @Test
    void br03_totalHoursAreCompletedEntriesPlusTheElapsedTimeOfTheOpenOne() {
        service.checkIn(alice);
        clock.advance(Duration.ofHours(1));
        service.checkOut(alice); // 1h completed
        clock.advance(Duration.ofMinutes(30));
        service.checkIn(alice);
        clock.advance(Duration.ofMinutes(20)); // 20m elapsed

        DaySummary day = service.today(alice, ZoneOffset.UTC);

        assertEquals(Duration.ofMinutes(80), day.worked());
        assertEquals(Duration.ofHours(1), day.completed());
        assertEquals(Duration.ofMinutes(20), day.openElapsed());
    }

    @Test
    void br04_publicHolidaysDoNotChangeTheDay() {
        clock.set(Instant.parse("2026-12-25T10:00:00Z")); // Christmas Day
        service.checkOutWithMissingCheckIn(alice, Instant.parse("2026-12-25T08:00:00Z"));

        TimeTrackingPanel panel = openPanel();

        assertEquals(1, timelineRows(panel).size());
        assertTrue(text(panel).contains("Total hours today: 2h 0m"), text(panel));
        assertTrue(text(panel).contains("Friday, December 25, 2026"), text(panel));
    }

    // --- helpers --------------------------------------------------------------------------------

    private TimeTrackingPanel openPanel() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        // The test UI starts on the home view, which would be reused with stale data: visit another view first.
        navigate(LoginView.class);
        navigate(HomeView.class);
        return find(TimeTrackingPanel.class).single();
    }

    private Button button(Component scope, String testId) {
        return buttons(scope, testId).stream().findFirst()
                .orElseThrow(() -> new AssertionError("No button " + testId));
    }

    private List<Button> buttons(Component scope, String testId) {
        return find(Button.class).from(scope).all().stream()
                .filter(button -> testId.equals(button.getTestId())).toList();
    }

    private List<Div> timelineRows(Component scope) {
        return find(Div.class).from(scope).all().stream().filter(div -> div.hasClassName("timeline-row")).toList();
    }

    private List<String> texts(Component scope, String className) {
        return find(Span.class).from(scope).all().stream().filter(span -> span.hasClassName(className))
                .map(Span::getText).map(UC003ViewDailyTimesheet::normalize).toList();
    }

    private List<String> badges(Component scope) {
        return find(Badge.class).from(scope).all().stream().map(Badge::getText).toList();
    }

    /** The visible text of a component tree; parts that are hidden are left out. */
    private static String text(Component component) {
        return normalize(text(component.getElement()));
    }

    private static String text(Element element) {
        if (element.isTextNode()) {
            return element.getText();
        }
        if (!element.isVisible()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        element.getChildren().forEach(child -> text.append(' ').append(text(child)));
        return text.toString();
    }

    /** The time format separates the time from AM/PM with a narrow no-break space. */
    private static String normalize(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }
}
