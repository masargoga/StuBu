package com.stubu.specdriven.timesheet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.YearMonth;

/** The monthly timesheet of an employee. Its status decides what may still be changed. */
@Entity
@Table(name = "timesheet")
public class Timesheet {

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

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
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

    /** Hands the timesheet in for approval; only a draft can be submitted. */
    public void submit(Instant at) {
        if (status != TimesheetStatus.DRAFT) {
            throw new IllegalStateException("Only a draft can be submitted, but the status is " + status);
        }
        status = TimesheetStatus.SUBMITTED;
        submittedAt = at;
    }

    public void setStatus(TimesheetStatus status) {
        this.status = status;
    }
}
