package com.stubu.specdriven.admin;

import com.stubu.specdriven.employee.AdminAccess;
import com.stubu.specdriven.employee.EmployeeAdminService;
import com.stubu.specdriven.employee.EmployeeNotFoundException;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets administrators look at all employees and their timesheets (UC-014): the list with search, filters and
 * sorting, and one employee with the full record and the list of their timesheets. It only reads; changing employees
 * is {@link EmployeeAdminService}, deciding about timesheets is the managers' job.
 */
@Service
public class EmployeeOverviewService {

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

    /** An employee with all their timesheets, the latest month first. */
    public record Details(EmployeeRow employee, List<TimesheetLine> timesheets) {
    }

    private final EmployeeAdminService employees;
    private final TimesheetRepository timesheets;
    private final AdminAccess adminAccess;

    public EmployeeOverviewService(EmployeeAdminService employees, TimesheetRepository timesheets,
            AdminAccess adminAccess) {
        this.employees = employees;
        this.timesheets = timesheets;
        this.adminAccess = adminAccess;
    }

    /**
     * The employees that match the query, sorted as asked.
     *
     * @throws com.stubu.specdriven.employee.AdminOnlyException if the acting user is not an active administrator
     */
    @Transactional(readOnly = true)
    public List<EmployeeRow> search(long adminId, Query query) {
        String text = query.text() == null ? "" : query.text().strip().toLowerCase(Locale.ROOT);
        return employees.list(adminId).stream()
                .filter(employee -> text.isEmpty() || contains(employee.fullName(), text)
                        || contains(employee.email(), text) || contains(employee.departmentName(), text))
                .filter(employee -> query.active() == null || employee.active() == query.active())
                .filter(employee -> query.role() == null || employee.role() == query.role())
                .filter(employee -> query.departmentId() == null || employee.departmentId() == query.departmentId())
                .sorted(comparator(query))
                .toList();
    }

    /**
     * One employee with all their timesheets, whatever the status.
     *
     * @throws EmployeeNotFoundException if there is no such employee
     */
    @Transactional(readOnly = true)
    public Details details(long adminId, long employeeId) {
        adminAccess.require(adminId);
        EmployeeRow employee = employees.list(adminId).stream().filter(row -> row.id() == employeeId).findFirst()
                .orElseThrow(EmployeeNotFoundException::new);
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

    private static boolean contains(String value, String text) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(text);
    }

    private static Comparator<EmployeeRow> comparator(Query query) {
        Comparator<String> text = Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);
        Comparator<EmployeeRow> byName = Comparator.comparing(EmployeeRow::lastName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(EmployeeRow::firstName, String.CASE_INSENSITIVE_ORDER);
        Comparator<EmployeeRow> chosen = switch (query.sort() == null ? Sort.NAME : query.sort()) {
            case NAME -> byName;
            case EMAIL -> Comparator.comparing(EmployeeRow::email, text);
            case ROLE -> Comparator.comparing(EmployeeRow::role);
            case DEPARTMENT -> Comparator.comparing(EmployeeRow::departmentName, text);
            case MANAGER -> Comparator.comparing(EmployeeRow::managerName, text);
            case STATUS -> Comparator.comparing(row -> !row.active()); // active first
            case LAST_LOGIN -> Comparator.comparing(EmployeeRow::lastLoginAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())); // never signed in counts as oldest
        };
        Comparator<EmployeeRow> result = query.ascending() ? chosen : chosen.reversed();
        return result.thenComparing(byName);
    }
}
