package com.stubu.specdriven.region;

/** The region cannot be deleted; the reason says what still uses it. */
public class RegionInUseException extends RuntimeException {

    public enum Reason {
        /** Employees are assigned to the region. */
        EMPLOYEES,
        /** The region still has public holidays. */
        HOLIDAYS,
        /** It is the only region left. */
        LAST_REGION
    }

    private final Reason reason;
    private final long count;

    public RegionInUseException(Reason reason, long count) {
        super("Region cannot be deleted: " + reason);
        this.reason = reason;
        this.count = count;
    }

    public Reason getReason() {
        return reason;
    }

    /** How many employees or holidays use the region (0 for {@link Reason#LAST_REGION}). */
    public long getCount() {
        return count;
    }
}
