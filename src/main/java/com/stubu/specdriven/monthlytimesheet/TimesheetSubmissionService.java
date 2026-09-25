package com.stubu.specdriven.monthlytimesheet;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.notification.NotificationService;
import com.stubu.specdriven.notification.TimesheetSubmittedNotice;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.TimeEntry;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Submits an employee's monthly timesheet for approval (UC-006). The status change and its audit entry are
 * written in one transaction; the manager is notified afterwards, and a notification that cannot be delivered
 * never undoes the submission.
 */
@Service
public class TimesheetSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSubmissionService.class);
    private static final String ENTITY_TYPE = "Timesheet";
    static final String RESUBMISSION_REASON = "Resubmission after rejection";

    private final TimesheetService timesheets;
    private final TimesheetRepository timesheetRepository;
    private final TimeEntryRepository entries;
    private final EmployeeRepository employees;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TimesheetSubmissionService(TimesheetService timesheets, TimesheetRepository timesheetRepository,
            TimeEntryRepository entries, EmployeeRepository employees, AuditService auditService,
            NotificationService notifications, Clock clock, PlatformTransactionManager transactionManager) {
        this.timesheets = timesheets;
        this.timesheetRepository = timesheetRepository;
        this.entries = entries;
        this.employees = employees;
        this.auditService = auditService;
        this.notifications = notifications;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Submits the employee's timesheet for a month, or resubmits it after a rejection (UC-008). It must be a
     * draft or rejected, the month must be over in the given time zone, and it needs at least one work period, all
     * of them completed.
     *
     * @throws SubmissionRejectedException if the timesheet cannot be submitted; also when another session
     *                                     submitted it at the same moment
     */
    public void submit(long employeeId, YearMonth month, ZoneId zone) {
        Instant now = clock.instant();
        timesheets.getOrCreate(employeeId, month);
        boolean[] resubmission = new boolean[1];
        try {
            transaction.executeWithoutResult(status -> {
                Timesheet sheet = timesheetRepository.findByEmployeeIdAndYearAndMonth(employeeId, month.getYear(),
                        month.getMonthValue()).orElseThrow();
                List<TimeEntry> monthEntries = entries.findStartedBetween(employeeId,
                        month.atDay(1).atStartOfDay(zone).toInstant(),
                        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant());
                Optional<SubmitBlocker> blocker = SubmissionRules.blocker(month, sheet.getStatus(), monthEntries,
                        YearMonth.now(clock.withZone(zone)));
                if (blocker.isPresent()) {
                    throw new SubmissionRejectedException(blocker.get());
                }
                TimesheetStatus before = sheet.getStatus();
                resubmission[0] = before == TimesheetStatus.REJECTED;
                sheet.submit(now);
                timesheetRepository.saveAndFlush(sheet);
                auditService.record(employeeId, ENTITY_TYPE, sheet.getId(), AuditAction.SUBMIT,
                        json(before, month, null), json(TimesheetStatus.SUBMITTED, month, now),
                        resubmission[0] ? RESUBMISSION_REASON : null);
            });
        } catch (ObjectOptimisticLockingFailureException submittedMeanwhile) {
            throw new SubmissionRejectedException(SubmitBlocker.NOT_DRAFT);
        }
        notifyManager(employeeId, month, now, resubmission[0]);
    }

    private void notifyManager(long employeeId, YearMonth month, Instant submittedAt, boolean resubmission) {
        try {
            Employee employee = employees.findById(employeeId).orElseThrow();
            Optional<Employee> manager = Optional.ofNullable(employee.getManagerId()).flatMap(employees::findById);
            if (manager.isEmpty()) {
                log.warn("Timesheet {} of employee {} was submitted, but the employee has no manager to notify",
                        month, employeeId);
                return;
            }
            notifications.timesheetSubmitted(new TimesheetSubmittedNotice(manager.get().getEmail(),
                    manager.get().getFullName(), employee.getFullName(), month, submittedAt, resubmission));
        } catch (RuntimeException e) {
            log.error("The manager could not be notified about the timesheet {} of employee {}", month, employeeId,
                    e);
        }
    }

    /** Audit values are small JSON documents; nothing in them needs escaping. */
    private static String json(TimesheetStatus status, YearMonth month, Instant submittedAt) {
        return "{\"period\":\"" + month + "\",\"status\":\"" + status + "\",\"submittedAt\":"
                + (submittedAt == null ? "null" : "\"" + submittedAt + "\"") + "}";
    }
}
