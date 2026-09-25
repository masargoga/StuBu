package com.stubu.specdriven.retention;

import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long employee time records are kept ({@code stubu.retention.time-records}, an ISO-8601 period such as
 * {@code P1Y} for one year, or {@code P18M}). The current requirement is up to one year (spec.md section 32); the
 * value is configurable because the legal and business stakeholders still have to confirm it.
 *
 * @param timeRecords how long time entries and timesheets are kept; one year if not configured
 */
@ConfigurationProperties(prefix = "stubu.retention")
public record RetentionProperties(Period timeRecords) {

    public static final Period DEFAULT_TIME_RECORDS = Period.ofYears(1);

    public RetentionProperties {
        if (timeRecords == null) {
            timeRecords = DEFAULT_TIME_RECORDS;
        }
        if (timeRecords.isZero() || timeRecords.isNegative()) {
            throw new IllegalArgumentException("stubu.retention.time-records must be a positive period, for example P1Y");
        }
    }
}
