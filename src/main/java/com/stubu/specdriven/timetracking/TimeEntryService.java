package com.stubu.specdriven.timetracking;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.timesheet.TimesheetService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records the employee's work periods. All timestamps come from the server clock. Every operation acts
 * on the employee it is given, which callers take from the authenticated user, never from user input.
 *
 * <p>A change and its audit entry are written in one transaction. The "one open period per employee"
 * rule is enforced by the database, so it also holds when two sessions act at the same time.
 */
@Service
public class TimeEntryService {

    static final String ENTITY_TYPE = "TimeEntry";

    private final TimeEntryRepository entries;
    private final AuditService auditService;
    private final TimesheetService timesheets;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public TimeEntryService(TimeEntryRepository entries, AuditService auditService, TimesheetService timesheets,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.entries = entries;
        this.auditService = auditService;
        this.timesheets = timesheets;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** The time zone used when the user's own is not known. */
    public ZoneId defaultZone() {
        return clock.getZone();
    }

    /** The current server time. */
    public Instant now() {
        return clock.instant();
    }

    /** Today's date in the given time zone. */
    public LocalDate currentDate(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    /**
     * Starts a new work period at the current server time.
     *
     * @throws AlreadyCheckedInException if the employee already has an open work period
     */
    public TimeEntry checkIn(long employeeId) {
        entries.findByOpenEmployeeId(employeeId).ifPresent(open -> {
            throw new AlreadyCheckedInException(open.getCheckInAt());
        });
        Instant now = clock.instant();
        try {
            return create(TimeEntry.open(employeeId, now));
        } catch (DataIntegrityViolationException concurrent) {
            // Another session checked the employee in between the check above and the insert.
            throw entries.findByOpenEmployeeId(employeeId)
                    .<RuntimeException>map(open -> new AlreadyCheckedInException(open.getCheckInAt()))
                    .orElse(concurrent);
        }
    }

    /**
     * Replaces the check-in time of the open work period with the current server time.
     *
     * @throws NotCheckedInException if the employee has no open work period
     */
    public TimeEntry replaceCheckIn(long employeeId) {
        Instant now = clock.instant();
        return Objects.requireNonNull(transaction.execute(status -> {
            TimeEntry open = entries.findByOpenEmployeeId(employeeId).orElseThrow(NotCheckedInException::new);
            Instant previous = open.getCheckInAt();
            open.replaceCheckIn(now);
            TimeEntry saved = entries.saveAndFlush(open);
            auditService.record(employeeId, ENTITY_TYPE, saved.getId(), AuditAction.UPDATE,
                    json(previous, null), json(saved.getCheckInAt(), null), "Check-in replaced");
            return saved;
        }));
    }

    /**
     * Ends the open work period at the current server time.
     *
     * @throws NotCheckedInException if the employee has no open work period
     */
    public TimeEntry checkOut(long employeeId) {
        Instant now = clock.instant();
        return Objects.requireNonNull(transaction.execute(status -> {
            TimeEntry open = entries.findByOpenEmployeeId(employeeId).orElseThrow(NotCheckedInException::new);
            String before = json(open.getCheckInAt(), null);
            // Never record a check-out before the check-in, even if the clock was adjusted meanwhile.
            open.checkOut(now.isBefore(open.getCheckInAt()) ? open.getCheckInAt() : now);
            TimeEntry saved = entries.saveAndFlush(open);
            auditService.record(employeeId, ENTITY_TYPE, saved.getId(), AuditAction.UPDATE, before,
                    json(saved.getCheckInAt(), saved.getCheckOutAt()), "Check-out");
            return saved;
        }));
    }

    /**
     * Checks the employee out when they forgot to check in: records a completed work period from the
     * given start until the current server time.
     *
     * @param checkInAt when the employee started working
     * @throws InvalidWorkPeriodException if the start is missing, not in the past, or the period overlaps
     *                                    another of the employee's work periods
     * @throws AlreadyCheckedInException  if the employee has an open work period after all
     */
    public TimeEntry checkOutWithMissingCheckIn(long employeeId, Instant checkInAt) {
        Instant now = clock.instant();
        if (checkInAt == null) {
            throw new InvalidWorkPeriodException(InvalidWorkPeriodException.Reason.MISSING_START);
        }
        if (!checkInAt.isBefore(now)) {
            throw new InvalidWorkPeriodException(InvalidWorkPeriodException.Reason.START_NOT_IN_PAST);
        }
        entries.findByOpenEmployeeId(employeeId).ifPresent(open -> {
            throw new AlreadyCheckedInException(open.getCheckInAt());
        });
        if (entries.overlaps(employeeId, checkInAt, now, now)) {
            throw new InvalidWorkPeriodException(InvalidWorkPeriodException.Reason.OVERLAP);
        }
        return create(TimeEntry.completed(employeeId, checkInAt, now));
    }

    /**
     * The employee's day in the given time zone: the work periods that started today, plus an open one
     * that started earlier, with worked and break time.
     */
    public DaySummary today(long employeeId, ZoneId zone) {
        Instant now = clock.instant();
        LocalDate date = LocalDate.now(clock.withZone(zone));
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();

        List<TimeEntry> day = new ArrayList<>(entries.findStartedBetween(employeeId, from, to));
        entries.findByOpenEmployeeId(employeeId)
                .filter(open -> day.stream().noneMatch(entry -> entry.getId().equals(open.getId())))
                .ifPresent(day::add);
        day.sort(Comparator.comparing(TimeEntry::getCheckInAt));
        return DaySummary.of(date, day, now, lockedEntryIds(employeeId, day, zone));
    }

    /**
     * Corrects the times of a work period. The date of the check-in cannot change, the check-out (if any) must
     * come after the check-in, nothing may lie in the future, the period must not overlap another one, and the
     * timesheet of the month must still be a draft.
     *
     * @param checkOutAt the corrected check-out, or {@code null} to keep an open period open
     * @param reason     why the times were corrected; optional
     * @param zone       the time zone that decides which day and month an entry belongs to
     * @throws EntryNotFoundException     if the entry does not exist or belongs to another employee
     * @throws EntryLockedException       if the timesheet was already submitted
     * @throws InvalidWorkPeriodException if the corrected times are not acceptable
     */
    public TimeEntry correct(long employeeId, long entryId, Instant checkInAt, Instant checkOutAt, String reason,
            ZoneId zone) {
        Instant now = clock.instant();
        return Objects.requireNonNull(transaction.execute(status -> {
            TimeEntry entry = ownEntry(employeeId, entryId);
            requireCorrectable(entry, zone);
            validateCorrection(entry, checkInAt, checkOutAt, now, zone);
            String before = json(entry.getCheckInAt(), entry.getCheckOutAt());
            entry.correct(checkInAt, checkOutAt);
            TimeEntry saved = entries.saveAndFlush(entry);
            auditService.record(employeeId, ENTITY_TYPE, saved.getId(), AuditAction.UPDATE, before,
                    json(saved.getCheckInAt(), saved.getCheckOutAt()), blankToNull(reason));
            return saved;
        }));
    }

    /**
     * Deletes a work period. The audit entry keeps the deleted times.
     *
     * @throws EntryNotFoundException if the entry does not exist or belongs to another employee
     * @throws EntryLockedException   if the timesheet was already submitted
     */
    public void delete(long employeeId, long entryId, String reason, ZoneId zone) {
        transaction.executeWithoutResult(status -> {
            TimeEntry entry = ownEntry(employeeId, entryId);
            requireCorrectable(entry, zone);
            String before = json(entry.getCheckInAt(), entry.getCheckOutAt());
            entries.delete(entry);
            entries.flush();
            auditService.record(employeeId, ENTITY_TYPE, entryId, AuditAction.DELETE, before, null,
                    blankToNull(reason));
        });
    }

    private TimeEntry ownEntry(long employeeId, long entryId) {
        return entries.findById(entryId).filter(entry -> entry.getEmployeeId() == employeeId)
                .orElseThrow(EntryNotFoundException::new);
    }

    private void requireCorrectable(TimeEntry entry, ZoneId zone) {
        var status = timesheets.statusOf(entry.getEmployeeId(), monthOf(entry, zone));
        if (!status.allowsCorrections()) {
            throw new EntryLockedException(status);
        }
    }

    private void validateCorrection(TimeEntry entry, Instant checkInAt, Instant checkOutAt, Instant now,
            ZoneId zone) {
        if (checkInAt == null) {
            throw new InvalidWorkPeriodException(InvalidWorkPeriodException.Reason.MISSING_START);
        }
        if (!entry.isActive() && checkOutAt == null) {
            throw new InvalidWorkPeriodException(InvalidWorkPeriodException.Reason.CHECK_OUT_REQUIRED);
        }
        if (checkInAt.isAfter(now) || (checkOutAt != null && checkOutAt.isAfter(now))) {
            throw new InvalidWorkPeriodException(InvalidWorkPeriodException.Reason.IN_THE_FUTURE);
        }
        if (checkOutAt != null && !checkOutAt.isAfter(checkInAt)) {
            throw new InvalidWorkPeriodException(InvalidWorkPeriodException.Reason.CHECK_OUT_BEFORE_CHECK_IN);
        }
        if (!checkInAt.atZone(zone).toLocalDate().equals(entry.getCheckInAt().atZone(zone).toLocalDate())) {
            throw new InvalidWorkPeriodException(InvalidWorkPeriodException.Reason.DATE_CHANGED);
        }
        Instant end = checkOutAt != null ? checkOutAt : now;
        if (entries.overlapsOtherThan(entry.getEmployeeId(), entry.getId(), checkInAt, end, now)) {
            throw new InvalidWorkPeriodException(InvalidWorkPeriodException.Reason.OVERLAP);
        }
    }

    /** The entries of the day whose month's timesheet no longer allows corrections. */
    private Set<Long> lockedEntryIds(long employeeId, List<TimeEntry> day, ZoneId zone) {
        Map<YearMonth, Boolean> correctable = new HashMap<>();
        Set<Long> locked = new HashSet<>();
        for (TimeEntry entry : day) {
            boolean allowed = correctable.computeIfAbsent(monthOf(entry, zone),
                    month -> timesheets.statusOf(employeeId, month).allowsCorrections());
            if (!allowed) {
                locked.add(entry.getId());
            }
        }
        return locked;
    }

    private static YearMonth monthOf(TimeEntry entry, ZoneId zone) {
        return YearMonth.from(entry.getCheckInAt().atZone(zone));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }


    private TimeEntry create(TimeEntry entry) {
        return Objects.requireNonNull(transaction.execute(status -> {
            TimeEntry saved = entries.saveAndFlush(entry);
            auditService.record(saved.getEmployeeId(), ENTITY_TYPE, saved.getId(), AuditAction.CREATE, null,
                    json(saved.getCheckInAt(), saved.getCheckOutAt()),
                    saved.isActive() ? "Check-in" : "Check-out without check-in");
            return saved;
        }));
    }

    /** Audit values are small JSON documents; instants are ISO-8601 so no escaping is needed. */
    private static String json(Instant checkInAt, Instant checkOutAt) {
        return "{\"checkInAt\":" + quote(checkInAt) + ",\"checkOutAt\":" + quote(checkOutAt) + "}";
    }

    private static String quote(Instant instant) {
        return instant == null ? "null" : "\"" + instant + "\"";
    }
}
