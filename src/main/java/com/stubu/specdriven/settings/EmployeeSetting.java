package com.stubu.specdriven.settings;

import com.stubu.specdriven.base.TimestampListener;
import com.stubu.specdriven.base.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * The personal settings of one employee: one row per employee, one column per setting, empty meaning "not chosen,
 * use the default". The language is the first setting; others (a theme, for example) are added as further columns.
 */
@Entity
@Table(name = "employee_setting")
@EntityListeners(TimestampListener.class)
public class EmployeeSetting implements Timestamped {

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    /** The code of the chosen {@link AppLanguage}, or {@code null}. */
    @Column(length = 5)
    private String language;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeSetting() {
    }

    public EmployeeSetting(Long employeeId) {
        this.employeeId = employeeId;
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

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
