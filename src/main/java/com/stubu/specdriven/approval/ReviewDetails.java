package com.stubu.specdriven.approval;

import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheet;

/**
 * A timesheet as the reviewing manager sees it.
 *
 * @param canDecide whether the viewer may approve or reject it (managers, not administrators)
 */
public record ReviewDetails(long timesheetId, String employeeName, MonthlyTimesheet sheet, boolean canDecide) {
}
