package com.stubu.specdriven.base;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Sets the creation and change times of {@link Timestamped} entities from the application clock. */
@Component
public class TimestampListener {

    private final Clock clock;

    public TimestampListener(Clock clock) {
        this.clock = clock;
    }

    @PrePersist
    void created(Object entity) {
        if (entity instanceof Timestamped timestamped) {
            timestamped.stampCreated(clock.instant());
        }
    }

    @PreUpdate
    void updated(Object entity) {
        if (entity instanceof Timestamped timestamped) {
            timestamped.stampUpdated(clock.instant());
        }
    }
}
