package com.stubu.specdriven.base;

import java.time.Instant;

/**
 * An entity that records when it was created and last changed. The moments come from the application's {@link
 * java.time.Clock} (see {@link TimestampListener}), so they agree with the audit log and with a controlled clock in tests.
 */
public interface Timestamped {

    /** The entity is stored for the first time. */
    void stampCreated(Instant now);

    /** The entity is changed; entities that do not keep a change time ignore it. */
    default void stampUpdated(Instant now) {
    }
}
