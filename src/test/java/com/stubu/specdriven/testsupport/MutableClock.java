package com.stubu.specdriven.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can set and advance, so recorded timestamps are exact. */
public final class MutableClock extends Clock {

    private volatile Instant now;
    private final ZoneId zone;

    public MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public static MutableClock utc(Instant now) {
        return new MutableClock(now, ZoneOffset.UTC);
    }

    public void set(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClockView(this, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }

    /** The same instant as the parent clock, seen in another time zone. */
    private static final class MutableClockView extends Clock {
        private final MutableClock parent;
        private final ZoneId zone;

        private MutableClockView(MutableClock parent, ZoneId zone) {
            this.parent = parent;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClockView(parent, zone);
        }

        @Override
        public Instant instant() {
            return parent.instant();
        }
    }
}
