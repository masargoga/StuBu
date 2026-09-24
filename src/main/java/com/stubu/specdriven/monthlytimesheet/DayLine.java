package com.stubu.specdriven.monthlytimesheet;

import com.stubu.specdriven.timetracking.DaySummary;
import java.time.LocalDate;

/**
 * One calendar day of a monthly timesheet.
 *
 * @param day         the day's work periods with worked and break time
 * @param weekend     Saturday or Sunday
 * @param holidayName the name of the public holiday on this day, or {@code null}
 */
public record DayLine(DaySummary day, boolean weekend, String holidayName) {

    public LocalDate date() {
        return day.date();
    }

    public boolean isHoliday() {
        return holidayName != null;
    }
}
