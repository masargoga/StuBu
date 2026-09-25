package com.stubu.specdriven.usecases.uc013_admin_view_audit_logs;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Download;
import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import java.nio.file.Files;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-013 Administrator View Audit Logs in a real browser: the list, the filters, the paging, the details dialog
 * and the CSV export at the supported screen sizes. The behaviour behind it is covered by
 * {@code UC013AdminViewAuditLogs}.
 */
class UC013AdminViewAuditLogsE2E extends E2ETest {

    private static final String CAROL = "carol.admin@example.com";

    @Autowired
    AuditService auditService;
    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    /** Thirty check-ins by Alice and one approval by Bob, on top of what signing in writes. */
    @BeforeEach
    void fillTheLog() {
        long carol = employee(CAROL, "Carol", "Admin", Role.ADMIN, true).getId();
        long alice = employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true).getId();
        long bob = employee("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, true).getId();
        jdbc.update("delete from audit_log");
        clock.set(Instant.parse("2026-09-01T08:00:00Z"));
        for (int i = 0; i < 30; i++) {
            clock.set(Instant.parse("2026-09-01T08:00:00Z").plusSeconds(60L * i));
            auditService.record(alice, "TimeEntry", 100L + i, AuditAction.CREATE, null,
                    "{\"checkInAt\":\"2026-09-01T08:00:00Z\"}", "Check-in");
        }
        clock.set(Instant.parse("2026-09-02T08:00:00Z"));
        auditService.record(bob, "Timesheet", 7L, AuditAction.APPROVE, "{\"status\":\"SUBMITTED\"}",
                "{\"status\":\"APPROVED\",\"period\":\"2026-08\"}", "Looks good, thanks");
        auditService.record(carol, "Employee", alice, AuditAction.UPDATE, "{\"role\":\"EMPLOYEE\"}",
                "{\"role\":\"MANAGER\"}", null);
    }

    private void signInAndOpenLog(Viewport viewport) {
        open(viewport);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();
        page.navigate(url("/admin/audit"));
        page.getByTestId("audit-list").waitFor();
    }

    @Test
    void onlyAdministratorsSeeTheAuditLog() {
        open(DESKTOP);
        signIn("bob.manager@example.com");
        page.locator("[data-testid=check-in]").waitFor();
        assertThat(page.getByTestId("nav-audit")).hasCount(0);

        page.navigate(url("/admin/audit"));

        assertThat(page.getByTestId("audit-list")).hasCount(0);
        assertThat(page.getByTestId("audit-search")).hasCount(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theLogFitsEveryScreenSize(Viewport viewport) {
        open(viewport);
        signIn(CAROL);
        page.locator("[data-testid=check-in]").waitFor();
        if (viewport.width() < 1024) {
            page.getByTestId("drawer-toggle").click();
        }
        assertThat(page.getByTestId("nav-audit")).isVisible();
        page.getByTestId("nav-audit").click();
        page.getByTestId("audit-list").waitFor();

        assertThat(page.getByTestId("audit-row")).hasCount(25);
        assertThat(page.getByTestId("audit-summary")).containsText("entries");
        assertThat(page.getByTestId("audit-page")).containsText("Page 1 of 2");
        assertReadable(".audit-summary");
        assertReadable(".audit-changes");
        assertNoHorizontalOverflow();
        screenshot("log-" + viewport.name());

        page.getByTestId("audit-next").click();
        assertThat(page.getByTestId("audit-page")).containsText("Page 2 of 2");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void filtersNarrowTheResultsAndDetailsOpenInADialog(Viewport viewport) {
        signInAndOpenLog(viewport);

        page.locator("[data-testid=audit-user] input").fill("bob");
        page.getByTestId("audit-search").click();
        assertThat(page.getByTestId("audit-row")).hasCount(1);
        assertThat(page.getByTestId("audit-row")).containsText("Timesheet #7");
        assertThat(page.getByTestId("audit-row")).containsText("status: SUBMITTED → APPROVED");
        assertNoHorizontalOverflow();
        screenshot("filtered-" + viewport.name());

        page.getByTestId("audit-details").click();
        assertThat(page.locator(".audit-detail")).containsText("Bob Manager (bob.manager@example.com");
        assertThat(page.locator(".audit-detail")).containsText("Looks good, thanks");
        assertInsideViewport("[data-testid=audit-close]", viewport);
        assertReadable(".audit-detail-value");
        screenshot("details-" + viewport.name());
        page.getByTestId("audit-close").click();

        page.locator("[data-testid=audit-user] input").fill("nobody with this name");
        page.getByTestId("audit-search").click();
        assertThat(page.getByTestId("no-entries")).hasText("No audit logs match your filters.");
        assertReadable(".time-empty");

        page.getByTestId("audit-reset").click();
        assertThat(page.getByTestId("audit-row")).hasCount(25);
    }

    @Test
    void theMatchingEntriesAreDownloadedAsCsv() throws Exception {
        signInAndOpenLog(DESKTOP);
        page.locator("[data-testid=audit-user] input").fill("alice");
        page.getByTestId("audit-search").click();
        assertThat(page.getByTestId("audit-summary")).containsText("30 entries"); // Alice's check-ins

        Download download = page.waitForDownload(() -> page.getByTestId("audit-export").click());

        assertEquals("audit-log.csv", download.suggestedFilename());
        String csv = Files.readString(download.path());
        assertTrue(csv.contains("timestamp,user,user email,entity type,entity id,action,reason,old values,new values"),
                csv);
        assertEquals(31, csv.strip().split("\r\n").length, "The header and the 30 entries");
        assertTrue(csv.contains("alice.employee@example.com"), csv);
        assertTrue(!csv.contains("Bob Manager"), "Only the filtered entries");
    }

    private void assertInsideViewport(String selector, Viewport viewport) {
        double[] box = box(selector);
        assertTrue(box[0] >= 0 && box[0] + box[2] <= viewport.width() && box[1] >= 0
                && box[1] + box[3] <= viewport.height(), selector + " must be fully visible: "
                + java.util.Arrays.toString(box));
    }
}
