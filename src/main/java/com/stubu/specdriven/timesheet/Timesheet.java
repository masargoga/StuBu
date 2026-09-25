package com.stubu.specdriven.timesheet;

import com.stubu.specdriven.base.TimestampListener;
import com.stubu.specdriven.base.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.YearMonth;

/** The monthly timesheet of an employee. Its status decides what may still be changed. */
@Entity
@EntityListeners(TimestampListener.class)
@Table(name = "timesheet")
public class Timesheet implements Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "period_year", nullable = false, updatable = false)
    private int year;

    @Column(name = "period_month", nullable = false, updatable = false)
    private int month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TimesheetStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejected_by")
    private Long rejectedBy;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Timesheet() {
    }

    public Timesheet(Long employeeId, YearMonth period, TimesheetStatus status) {
        this.employeeId = employeeId;
        this.year = period.getYear();
        this.month = period.getMonthValue();
        this.status = status;
    }

    @Override
    public void stampCreated(Instant now) {
        createdAt = now;
        updatedAt = now;
    }

    @Override
    public void stampUpdated(Instant now) {
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public YearMonth getPeriod() {
        return YearMonth.of(year, month);
    }

    public TimesheetStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    /** The manager who approved the timesheet, if it was approved. */
    public Long getApprovedBy() {
        return approvedBy;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public Long getRejectedBy() {
        return rejectedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    /**
     * Hands the timesheet in for approval; a draft is submitted, a rejected timesheet resubmitted. The details of
     * an earlier rejection stay on the record.
     */
    public void submit(Instant at) {
        if (status != TimesheetStatus.DRAFT && status != TimesheetStatus.REJECTED) {
            throw new IllegalStateException("Only a draft or rejected timesheet can be submitted, but the status is "
                    + status);
        }
        status = TimesheetStatus.SUBMITTED;
        submittedAt = at;
    }

    /** A manager approves a submitted timesheet; from now on it can no longer be changed. */
    public void approve(Instant at, Long reviewerId) {
        requireSubmitted();
        status = TimesheetStatus.APPROVED;
        approvedAt = at;
        approvedBy = reviewerId;
    }

    /** A manager sends a submitted timesheet back, with the reason, so the employee can correct it. */
    public void reject(Instant at, Long reviewerId, String reason) {
        requireSubmitted();
        status = TimesheetStatus.REJECTED;
        rejectedAt = at;
        rejectedBy = reviewerId;
        rejectionReason = reason;
    }

    private void requireSubmitted() {
        if (status != TimesheetStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted timesheet can be reviewed, but the status is " + status);
        }
    }

    public void setStatus(TimesheetStatus status) {
        this.status = status;
    }
}
