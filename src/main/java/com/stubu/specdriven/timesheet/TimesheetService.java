package com.stubu.specdriven.timesheet;

import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimesheetService {

    private final TimesheetRepository timesheets;

    public TimesheetService(TimesheetRepository timesheets) {
        this.timesheets = timesheets;
    }

    /**
     * The status of the employee's timesheet for a month. A month without a timesheet record is a draft:
     * the record is created when the month is first opened as a timesheet.
     */
    @Transactional(readOnly = true)
    public TimesheetStatus statusOf(long employeeId, YearMonth period) {
        return timesheets.findByEmployeeIdAndYearAndMonth(employeeId, period.getYear(), period.getMonthValue())
                .map(Timesheet::getStatus)
                .orElse(TimesheetStatus.DRAFT);
    }
}
