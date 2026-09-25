package com.stubu.specdriven.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The retention period is configurable and only reports what is beyond it (spec.md section 32). */
class RetentionPolicyTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC);

    private static RetentionProperties bound(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bind("stubu.retention", RetentionProperties.class)
                .orElseGet(() -> new RetentionProperties(null));
    }

    @Test
    void withoutConfigurationTimeRecordsAreKeptForOneYear() {
        RetentionPolicy policy = new RetentionPolicy(bound(Map.of()), NOW);

        assertEquals(Period.ofYears(1), policy.timeRecords());
        assertEquals(LocalDate.of(2025, 10, 5), policy.retainedSince());
    }

    @Test
    void thePeriodCanBeConfigured() {
        RetentionPolicy policy = new RetentionPolicy(bound(Map.of("stubu.retention.time-records", "P18M")), NOW);

        assertEquals(Period.ofMonths(18), policy.timeRecords());
        assertEquals(LocalDate.of(2025, 4, 5), policy.retainedSince());
    }

    @Test
    void recordsOlderThanThePeriodAreBeyondRetention() {
        RetentionPolicy policy = new RetentionPolicy(new RetentionProperties(Period.ofYears(1)), NOW);

        assertTrue(policy.isBeyondRetention(Instant.parse("2025-10-04T23:59:59Z")));
        assertFalse(policy.isBeyondRetention(Instant.parse("2025-10-05T00:00:00Z")), "The first retained day");
        assertFalse(policy.isBeyondRetention(Instant.parse("2026-09-30T12:00:00Z")));
    }

    @Test
    void aPeriodThatIsNotPositiveIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new RetentionProperties(Period.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RetentionProperties(Period.ofDays(-1)));
    }
}
