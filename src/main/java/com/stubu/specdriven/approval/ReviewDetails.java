package com.stubu.specdriven.approval;

import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheet;

/** A timesheet as the reviewing manager sees it. */
public record ReviewDetails(long timesheetId, String employeeName, MonthlyTimesheet sheet) {
}
