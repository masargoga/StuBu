package com.stubu.specdriven.admin;

import com.stubu.specdriven.employee.AdminAccess;
import com.stubu.specdriven.employee.EmployeeNotFoundException;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets administrators look at all employees and their timesheets (UC-014): the list with search, filters and
 * sorting, page by page, and one employee with the full record and the list of their timesheets. It only reads;
 * changing employees is {@link com.stubu.specdriven.employee.EmployeeAdminService}, deciding about timesheets is the
 * managers' job.
 */
@Service
public class EmployeeOverviewService {

    /** How many employees one page of the list shows. */
    public static final int PAGE_SIZE = 25;

    /** The columns the list can be sorted by. */
    public enum Sort {
        NAME, EMAIL, ROLE, DEPARTMENT, MANAGER, STATUS, LAST_LOGIN
    }

    /**
     * What to show.
     *
     * @param text         part of the name, email address or department; not case-sensitive
     * @param active       {@code true} for active, {@code false} for inactive employees, {@code null} for both
     * @param role         only this role, or {@code null}
     * @param departmentId only this department, or {@code null}
     */
    public record Query(String text, Boolean active, Role role, Long departmentId, Sort sort, boolean ascending) {

        public static final Query ALL = new Query(null, null, null, null, Sort.NAME, true);

        /** Whether anything narrows the list. */
        public boolean isFiltered() {
            return (text != null && !text.isBlank()) || active != null || role != null || departmentId != null;
        }
    }

    /**
     * One of an employee's timesheets.
     *
     * @param decidedAt          when it was approved or rejected, if it was
     * @param rejectionReason    the reason of the latest rejection, if there was one
     */
    public record TimesheetLine(long id, YearMonth period, TimesheetStatus status, Instant submittedAt,
            Instant decidedAt, String rejectionReason) {
    }

    /**
     * One page of the list.
     *
     * @param rows          the employees of this page
     * @param total         how many employees match in all
     * @param page          the number of this page, counting from 0
     * @param pages         how many pages there are (at least 1)
     * @param anyEmployees  whether there are employees at all, whatever the filters
     */
    public record Page(List<EmployeeRow> rows, long total, int page, int pages, boolean anyEmployees) {
    }

    /** An employee with all their timesheets, the latest month first. */
    public record Details(EmployeeRow employee, List<TimesheetLine> timesheets) {
    }

    private final EmployeeSearch search;
    private final TimesheetRepository timesheets;
    private final AdminAccess adminAccess;

    public EmployeeOverviewService(EmployeeSearch search, TimesheetRepository timesheets,
            AdminAccess adminAccess) {
        this.search = search;
        this.timesheets = timesheets;
        this.adminAccess = adminAccess;
    }

    /**
     * All employees that match the query, sorted as asked. The list of the administrator shows them page by page, see
     * {@link #page}.
     *
     * @throws com.stubu.specdriven.employee.AdminOnlyException if the acting user is not an active administrator
     */
    @Transactional(readOnly = true)
    public List<EmployeeRow> search(long adminId, Query query) {
        adminAccess.require(adminId);
        return search.find(query, 0, Integer.MAX_VALUE);
    }

    /**
     * One page of the employees that match the query, sorted as asked.
     *
     * @param page the page to show, counting from 0; a page beyond the last one gives the last page
     * @throws com.stubu.specdriven.employee.AdminOnlyException if the acting user is not an active administrator
     */
    @Transactional(readOnly = true)
    public Page page(long adminId, Query query, int page) {
        adminAccess.require(adminId);
        long total = search.count(query);
        int pages = (int) Math.max((total + PAGE_SIZE - 1) / PAGE_SIZE, 1);
        int shown = Math.min(Math.max(page, 0), pages - 1);
        List<EmployeeRow> rows = total == 0 ? List.of() : search.find(query, shown * PAGE_SIZE, PAGE_SIZE);
        boolean any = total > 0 || (query.isFiltered() && search.count(Query.ALL) > 0);
        return new Page(rows, total, shown, pages, any);
    }

    /**
     * One employee with all their timesheets, whatever the status.
     *
     * @throws EmployeeNotFoundException if there is no such employee
     */
    @Transactional(readOnly = true)
    public Details details(long adminId, long employeeId) {
        adminAccess.require(adminId);
        EmployeeRow employee = search.find(employeeId).orElseThrow(EmployeeNotFoundException::new);
        List<TimesheetLine> lines = timesheets.findByEmployeeIdOrderByYearDescMonthDesc(employeeId).stream()
                .map(EmployeeOverviewService::line).toList();
        return new Details(employee, lines);
    }

    private static TimesheetLine line(Timesheet sheet) {
        Instant decidedAt = switch (sheet.getStatus()) {
            case APPROVED -> sheet.getApprovedAt();
            case REJECTED -> sheet.getRejectedAt();
            default -> null;
        };
        return new TimesheetLine(sheet.getId(), sheet.getPeriod(), sheet.getStatus(), sheet.getSubmittedAt(),
                decidedAt, sheet.getRejectionReason());
    }
}
