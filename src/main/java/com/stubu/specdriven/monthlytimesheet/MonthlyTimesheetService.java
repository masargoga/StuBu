package com.stubu.specdriven.monthlytimesheet;

import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.holiday.PublicHolidayRepository;
import com.stubu.specdriven.region.Region;
import com.stubu.specdriven.region.RegionRepository;
import com.stubu.specdriven.timesheet.Timesheet;
import com.stubu.specdriven.timesheet.TimesheetService;
import com.stubu.specdriven.timetracking.DaySummary;
import com.stubu.specdriven.timetracking.TimeEntry;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** Builds the monthly timesheet an employee reviews before submitting it. */
@Service
public class MonthlyTimesheetService {

    private final TimesheetService timesheets;
    private final TimeEntryRepository entries;
    private final PublicHolidayRepository holidays;
    private final RegionRepository regions;
    private final EmployeeRepository employees;
    private final Clock clock;

    public MonthlyTimesheetService(TimesheetService timesheets, TimeEntryRepository entries,
            PublicHolidayRepository holidays, RegionRepository regions, EmployeeRepository employees, Clock clock) {
        this.timesheets = timesheets;
        this.entries = entries;
        this.holidays = holidays;
        this.regions = regions;
        this.employees = employees;
        this.clock = clock;
    }

    /** Today in the given time zone. */
    public LocalDate today(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    /** The current month in the given time zone: the latest month that may be shown. */
    public YearMonth currentMonth(ZoneId zone) {
        return YearMonth.now(clock.withZone(zone));
    }

    /**
     * Loads the employee's timesheet for a month, creating it as a draft on first access. A work period belongs
     * to the day it started on, in the given time zone.
     *
     * @throws IllegalArgumentException for a month in the future, which is never shown
     */
    public MonthlyTimesheet load(long employeeId, YearMonth month, ZoneId zone) {
        if (month.isAfter(currentMonth(zone))) {
            throw new IllegalArgumentException("Future months are not displayed: " + month);
        }
        return build(timesheets.getOrCreate(employeeId, month), employeeId, month, zone);
    }

    /**
     * Like {@link #load}, but for looking at somebody else's timesheet: nothing is created, and a month for which
     * the employee never opened a timesheet gives an empty result.
     */
    public Optional<MonthlyTimesheet> loadIfExists(long employeeId, YearMonth month, ZoneId zone) {
        return timesheets.find(employeeId, month).map(timesheet -> build(timesheet, employeeId, month, zone));
    }

    private MonthlyTimesheet build(Timesheet timesheet, long employeeId, YearMonth month, ZoneId zone) {
        Instant now = clock.instant();

        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Map<LocalDate, List<TimeEntry>> byDay = new TreeMap<>();
        List<TimeEntry> monthEntries = entries.findStartedBetween(employeeId, from, to);
        for (TimeEntry entry : monthEntries) {
            byDay.computeIfAbsent(entry.getCheckInAt().atZone(zone).toLocalDate(), day -> new ArrayList<>())
                    .add(entry);
        }
        // The holidays are those of the region of the employee whose timesheet this is, whoever looks at it (UC-017).
        Long regionId = employees.findById(employeeId).map(Employee::getRegionId).orElse(Region.DEFAULT_ID);
        String regionName = regions.findById(regionId).map(Region::getName).orElse(null);
        Map<LocalDate, String> holidayNames = new TreeMap<>();
        holidays.findByRegionIdAndDateBetweenOrderByDate(regionId, month.atDay(1), month.atEndOfMonth())
                .forEach(holiday -> holidayNames.put(holiday.getDate(), holiday.getName()));

        // When the timesheet is not a draft, none of its entries can be corrected.
        Set<Long> locked = new HashSet<>();
        if (!timesheet.getStatus().allowsCorrections()) {
            byDay.values().forEach(day -> day.forEach(entry -> locked.add(entry.getId())));
        }

        List<DayLine> days = new ArrayList<>();
        Duration totalWorked = Duration.ZERO;
        Duration totalBreaks = Duration.ZERO;
        for (int dayOfMonth = 1; dayOfMonth <= month.lengthOfMonth(); dayOfMonth++) {
            LocalDate date = month.atDay(dayOfMonth);
            DaySummary summary = DaySummary.of(date, byDay.getOrDefault(date, List.of()), now, locked);
            DayOfWeek weekday = date.getDayOfWeek();
            days.add(new DayLine(summary, weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY,
                    holidayNames.get(date)));
            totalWorked = totalWorked.plus(summary.worked());
            totalBreaks = totalBreaks.plus(summary.breaks());
        }
        return new MonthlyTimesheet(month, regionName, timesheet.getStatus(), timesheet.getSubmittedAt(),
                timesheet.getApprovedAt(), nameOf(timesheet.getApprovedBy()), timesheet.getRejectedAt(),
                timesheet.getRejectionReason(), List.copyOf(days), totalWorked, totalBreaks,
                SubmissionRules.blocker(month, timesheet.getStatus(), monthEntries, currentMonth(zone)).orElse(null));
    }

    private String nameOf(Long employeeId) {
        return employeeId == null ? null : employees.findById(employeeId).map(Employee::getFullName).orElse(null);
    }
}
