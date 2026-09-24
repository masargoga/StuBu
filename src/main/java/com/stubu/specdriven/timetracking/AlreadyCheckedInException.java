package com.stubu.specdriven.timetracking;

import java.time.Instant;

/** The employee already has an open work period. */
public class AlreadyCheckedInException extends RuntimeException {

    private final transient Instant since;

    public AlreadyCheckedInException(Instant since) {
        super("Already checked in since " + since);
        this.since = since;
    }

    /** The check-in time of the open work period. */
    public Instant getSince() {
        return since;
    }
}
