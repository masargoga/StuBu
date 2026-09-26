package com.stubu.specdriven.region;

import com.stubu.specdriven.base.TimestampListener;
import com.stubu.specdriven.base.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A region: the area whose public holidays apply to the employees who work there (UC-017), for example a country or
 * a state. Every employee and every public holiday belongs to exactly one region.
 */
@Entity
@EntityListeners(TimestampListener.class)
@Table(name = "region")
public class Region implements Timestamped {

    /** The id of the region "Default", the first row of the table: the migration puts existing data there. */
    public static final long DEFAULT_ID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Counts the changes; a save based on an older version is refused. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Region() {
    }

    public Region(String name) {
        this.name = name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
