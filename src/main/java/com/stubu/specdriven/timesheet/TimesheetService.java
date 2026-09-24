package com.stubu.specdriven.timesheet;

import java.time.YearMonth;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TimesheetService {

    private final TimesheetRepository timesheets;
    private final TransactionTemplate transaction;

    public TimesheetService(TimesheetRepository timesheets, PlatformTransactionManager transactionManager) {
        this.timesheets = timesheets;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * The status of the employee's timesheet for a month. A month without a timesheet record is a draft:
     * the record is created when the month is first opened as a timesheet.
     */
    @Transactional(readOnly = true)
    public TimesheetStatus statusOf(long employeeId, YearMonth period) {
        return find(employeeId, period).map(Timesheet::getStatus).orElse(TimesheetStatus.DRAFT);
    }

    /** The employee's timesheet for a month, created as a DRAFT on first access. */
    public Timesheet getOrCreate(long employeeId, YearMonth period) {
        var existing = find(employeeId, period);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return transaction.execute(status -> timesheets.saveAndFlush(
                    new Timesheet(employeeId, period, TimesheetStatus.DRAFT)));
        } catch (DataIntegrityViolationException createdMeanwhile) {
            // Another session opened the same month first: use its record.
            return find(employeeId, period).orElseThrow(() -> createdMeanwhile);
        }
    }

    private Optional<Timesheet> find(long employeeId, YearMonth period) {
        return timesheets.findByEmployeeIdAndYearAndMonth(employeeId, period.getYear(), period.getMonthValue());
    }
}
