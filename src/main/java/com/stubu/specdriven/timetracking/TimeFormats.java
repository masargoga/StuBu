package com.stubu.specdriven.timetracking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Presents instants in the user's time zone and locale. */
public final class TimeFormats {

    private TimeFormats() {
    }

    /** The time of day, e.g. "8:03 AM" or "08:03" depending on the locale. */
    public static String time(Instant instant, ZoneId zone, Locale locale) {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant);
    }

    /** The time of day, with the date when it is not on {@code reference}. */
    public static String timeOrDateTime(Instant instant, ZoneId zone, Locale locale, LocalDate reference) {
        if (instant.atZone(zone).toLocalDate().equals(reference)) {
            return time(instant, zone, locale);
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT).withLocale(locale)
                .withZone(zone).format(instant);
    }

    /** The full date, e.g. "Wednesday, September 23, 2026". */
    public static String fullDate(LocalDate date, Locale locale) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date);
    }
}
