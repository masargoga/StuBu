package com.stubu.specdriven.approval;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetService;
import com.stubu.specdriven.notification.NotificationService;
import com.stubu.specdriven.notification.TimesheetDecisionNotice;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetRepository;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lists the submitted timesheets a manager may decide about and approves or rejects them (UC-007). Every
 * operation acts for the reviewer it is given, which callers take from the authenticated user; who may review
 * whom is decided by {@link ReviewerAuthorization}. A decision and its audit entry are stored in one
 * transaction; the employee is notified afterwards, and a notification that cannot be delivered never undoes
 * the decision.
 */
@Service
public class TimesheetReviewService {

    static final int MAX_REASON_LENGTH = 1000;
    private static final Logger log = LoggerFactory.getLogger(TimesheetReviewService.class);
    private static final String ENTITY_TYPE = "Timesheet";

    private final TimesheetRepository timesheets;
    private final EmployeeRepository employees;
    private final ReviewerAuthorization authorization;
    private final MonthlyTimesheetService monthly;
    private final AuditService auditService;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TimesheetReviewService(TimesheetRepository timesheets, EmployeeRepository employees,
            ReviewerAuthorization authorization, MonthlyTimesheetService monthly, AuditService auditService,
            NotificationService notifications, Clock clock, PlatformTransactionManager transactionManager) {
        this.timesheets = timesheets;
        this.employees = employees;
        this.authorization = authorization;
        this.monthly = monthly;
        this.auditService = auditService;
        this.notifications = notifications;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * The submitted timesheets of the reviewer's employees, oldest submission first. A user who may not
     * review anybody gets an empty list.
     */
    @Transactional(readOnly = true)
    public List<PendingApproval> pending(long reviewerId, ReviewScope scope) {
        Employee reviewer = employees.findById(reviewerId).orElseThrow(ReviewNotAllowedException::new);
        List<Employee> candidates = switch (scope) {
            case DIRECT_REPORTS -> employees.findByManagerId(reviewer.getId());
            case DEPARTMENT -> reviewer.getRole() == Role.ADMIN ? employees.findAll()
                    : employees.findByDepartmentId(reviewer.getDepartmentId());
        };
        Map<Long, Employee> subjects = candidates.stream().filter(candidate -> authorization.mayReview(reviewer,
                candidate)).collect(Collectors.toMap(Employee::getId, Function.identity()));
        if (subjects.isEmpty()) {
            return List.of();
        }
        return timesheets.findByStatusAndEmployeeIdInOrderBySubmittedAtAsc(TimesheetStatus.SUBMITTED,
                subjects.keySet()).stream()
                .map(sheet -> new PendingApproval(sheet.getId(), subjects.get(sheet.getEmployeeId()).getFullName(),
                        sheet.getPeriod(), sheet.getSubmittedAt()))
                .toList();
    }

    /**
     * The timesheet to review, with all its days.
     *
     * @param zone the time zone that decides which day an entry belongs to
     * @throws ReviewNotAllowedException if there is no such timesheet or the reviewer may not see it
     */
    @Transactional
    public ReviewDetails details(long reviewerId, long timesheetId, ZoneId zone) {
        Timesheet sheet = timesheets.findById(timesheetId).orElseThrow(ReviewNotAllowedException::new);
        Employee reviewer = employees.findById(reviewerId).orElseThrow(ReviewNotAllowedException::new);
        Employee subject = employees.findById(sheet.getEmployeeId()).orElseThrow(ReviewNotAllowedException::new);
        if (!authorization.mayReview(reviewer, subject)) {
            throw new ReviewNotAllowedException();
        }
        return new ReviewDetails(sheet.getId(), subject.getFullName(),
                monthly.load(subject.getId(), sheet.getPeriod(), zone));
    }

    /**
     * Approves a submitted timesheet; it can no longer be changed afterwards.
     *
     * @throws ReviewNotAllowedException if there is no such timesheet or the reviewer may not review it
     * @throws ReviewStatusException     if the timesheet is not (or no longer) submitted
     */
    public void approve(long reviewerId, long timesheetId) {
        Instant now = clock.instant();
        Decision decision = decide(reviewerId, timesheetId, null, now);
        notifyEmployee(decision, true);
    }

    /**
     * Rejects a submitted timesheet. The employee can then correct their entries.
     *
     * @param reason why it is rejected; mandatory
     * @throws ReviewNotAllowedException if there is no such timesheet or the reviewer may not review it
     * @throws ReviewStatusException     if the timesheet is not (or no longer) submitted
     * @throws InvalidReasonException    if the reason is missing or too long
     */
    public void reject(long reviewerId, long timesheetId, String reason) {
        Instant now = clock.instant();
        String cleaned = reason == null ? "" : reason.strip();
        Decision decision = decide(reviewerId, timesheetId, cleaned, now);
        notifyEmployee(decision, false);
    }

    private record Decision(Employee reviewer, Employee subject, Timesheet sheet, Instant at, String reason) {
    }

    /** {@code reason == null} means approve, anything else reject. */
    private Decision decide(long reviewerId, long timesheetId, String reason, Instant now) {
        try {
            return transaction.execute(status -> {
                Timesheet sheet = timesheets.findById(timesheetId).orElseThrow(ReviewNotAllowedException::new);
                Employee reviewer = employees.findById(reviewerId).orElseThrow(ReviewNotAllowedException::new);
                Employee subject = employees.findById(sheet.getEmployeeId())
                        .orElseThrow(ReviewNotAllowedException::new);
                if (!authorization.mayReview(reviewer, subject)) {
                    throw new ReviewNotAllowedException();
                }
                if (sheet.getStatus() != TimesheetStatus.SUBMITTED) {
                    throw new ReviewStatusException(sheet.getStatus());
                }
                if (reason != null) {
                    if (reason.isEmpty()) {
                        throw new InvalidReasonException(InvalidReasonException.Problem.REQUIRED);
                    }
                    if (reason.length() > MAX_REASON_LENGTH) {
                        throw new InvalidReasonException(InvalidReasonException.Problem.TOO_LONG);
                    }
                }
                TimesheetStatus before = sheet.getStatus();
                if (reason == null) {
                    sheet.approve(now, reviewerId);
                } else {
                    sheet.reject(now, reviewerId, reason);
                }
                timesheets.saveAndFlush(sheet);
                auditService.record(reviewerId, ENTITY_TYPE, sheet.getId(),
                        reason == null ? AuditAction.APPROVE : AuditAction.REJECT, json(before, sheet, null),
                        json(sheet.getStatus(), sheet, now), reason);
                return new Decision(reviewer, subject, sheet, now, reason);
            });
        } catch (ObjectOptimisticLockingFailureException decidedMeanwhile) {
            // Another session decided at the same moment: report what the timesheet is now.
            TimesheetStatus current = timesheets.findById(timesheetId).map(Timesheet::getStatus)
                    .orElse(TimesheetStatus.SUBMITTED);
            throw new ReviewStatusException(current);
        }
    }

    private void notifyEmployee(Decision decision, boolean approved) {
        try {
            TimesheetDecisionNotice notice = new TimesheetDecisionNotice(decision.subject().getEmail(),
                    decision.subject().getFullName(), decision.reviewer().getFullName(), decision.sheet().getPeriod(),
                    decision.at(), decision.reason());
            if (approved) {
                notifications.timesheetApproved(notice);
            } else {
                notifications.timesheetRejected(notice);
            }
        } catch (RuntimeException e) {
            log.error("The employee could not be notified about the decision on timesheet {}",
                    decision.sheet().getId(), e);
        }
    }

    /** Audit values are small JSON documents; nothing in them needs escaping. */
    private static String json(TimesheetStatus status, Timesheet sheet, Instant decidedAt) {
        return "{\"period\":\"" + sheet.getPeriod() + "\",\"employeeId\":" + sheet.getEmployeeId()
                + ",\"status\":\"" + status + "\",\"decidedAt\":"
                + (decidedAt == null ? "null" : "\"" + decidedAt + "\"") + "}";
    }
}
