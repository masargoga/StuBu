package com.stubu.specdriven.retention;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tells which time records are older than the configured retention period. It only answers the question: nothing is
 * deleted, because deleting employee time records needs explicit business requirements, a look at the audit
 * implications and a legal review first (spec.md section 32). A later deletion job (or an anonymising one) can build
 * on {@link #retainedSince()}.
 */
@Component
@EnableConfigurationProperties(RetentionProperties.class)
public class RetentionPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetentionPolicy.class);

    private final RetentionProperties properties;
    private final Clock clock;

    public RetentionPolicy(RetentionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** How long time records are kept. */
    public Period timeRecords() {
        return properties.timeRecords();
    }

    /** The day from which on time records are still within the retention period; older ones may be removed. */
    public LocalDate retainedSince() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC)).minus(properties.timeRecords());
    }

    /** Whether a record of this moment is older than the retention period. */
    public boolean isBeyondRetention(Instant recordedAt) {
        return recordedAt.atOffset(ZoneOffset.UTC).toLocalDate().isBefore(retainedSince());
    }

    @EventListener(ApplicationReadyEvent.class)
    void logPolicy() {
        log.info("Time records are kept for {} (from {} on); nothing is deleted automatically", properties.timeRecords(),
                retainedSince());
    }
}
