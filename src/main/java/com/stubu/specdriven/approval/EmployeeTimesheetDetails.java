package com.stubu.specdriven.approval;

import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheet;
import java.util.List;

/**
 * An employee's timesheet for one month as a manager sees it.
 *
 * @param timesheetId the timesheet, or {@code null} if the employee has no timesheet for that month
 * @param sheet       the days and totals, or {@code null} if there is no timesheet
 * @param history     submission and decisions, oldest first; empty if there is no timesheet
 * @param canDecide   whether the viewer may approve or reject it (managers, not administrators)
 */
public record EmployeeTimesheetDetails(long employeeId, String employeeName, Role role, Long timesheetId,
        MonthlyTimesheet sheet, List<HistoryEntry> history, boolean canDecide) {

    public boolean exists() {
        return sheet != null;
    }
}
