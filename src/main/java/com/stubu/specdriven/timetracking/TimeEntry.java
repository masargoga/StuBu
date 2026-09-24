package com.stubu.specdriven.timetracking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;

/**
 * A work period of an employee: from check-in to check-out, both absolute instants. A period without
 * check-out is open (the employee is currently working). At most one open period per employee exists;
 * the database enforces that through the unique {@code openEmployeeId} column.
 */
@Entity
@Table(name = "time_entry")
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "check_in_at", nullable = false)
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    /** Equals {@code employeeId} while the period is open, otherwise {@code null}. */
    @Column(name = "open_employee_id", unique = true)
    private Long openEmployeeId;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimeEntry() {
    }

    private TimeEntry(Long employeeId, Instant checkInAt, Instant checkOutAt) {
        this.employeeId = employeeId;
        this.checkInAt = checkInAt;
        this.checkOutAt = checkOutAt;
        this.openEmployeeId = checkOutAt == null ? employeeId : null;
    }

    /** A new open period: the employee is working since {@code checkInAt}. */
    public static TimeEntry open(Long employeeId, Instant checkInAt) {
        return new TimeEntry(employeeId, checkInAt, null);
    }

    /** A new completed period. */
    public static TimeEntry completed(Long employeeId, Instant checkInAt, Instant checkOutAt) {
        if (checkOutAt.isBefore(checkInAt)) {
            throw new IllegalArgumentException("Check-out must not be before check-in");
        }
        return new TimeEntry(employeeId, checkInAt, checkOutAt);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Ends this open period. */
    public void checkOut(Instant at) {
        if (!isActive()) {
            throw new IllegalStateException("The work period is not open");
        }
        if (at.isBefore(checkInAt)) {
            throw new IllegalArgumentException("Check-out must not be before check-in");
        }
        checkOutAt = at;
        openEmployeeId = null;
    }

    /** Replaces the check-in time of this open period. */
    public void replaceCheckIn(Instant at) {
        if (!isActive()) {
            throw new IllegalStateException("The work period is not open");
        }
        checkInAt = at;
    }

    /**
     * Corrects both times. A completed period stays completed; an open period may be closed by giving a
     * check-out or kept open with {@code null}.
     */
    public void correct(Instant newCheckInAt, Instant newCheckOutAt) {
        if (newCheckOutAt == null && !isActive()) {
            throw new IllegalStateException("A completed work period cannot be reopened");
        }
        if (newCheckOutAt != null && newCheckOutAt.isBefore(newCheckInAt)) {
            throw new IllegalArgumentException("Check-out must not be before check-in");
        }
        checkInAt = newCheckInAt;
        checkOutAt = newCheckOutAt;
        openEmployeeId = newCheckOutAt == null ? employeeId : null;
    }


    /** True while the employee is working, i.e. there is no check-out yet. */
    public boolean isActive() {
        return checkOutAt == null;
    }

    /** The length of the period up to {@code now} for an open period, or its full length once completed. */
    public Duration durationAt(Instant now) {
        Instant end = checkOutAt != null ? checkOutAt : now;
        return end.isBefore(checkInAt) ? Duration.ZERO : Duration.between(checkInAt, end);
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Instant getCheckInAt() {
        return checkInAt;
    }

    public Instant getCheckOutAt() {
        return checkOutAt;
    }

    public Long getOpenEmployeeId() {
        return openEmployeeId;
    }
}
