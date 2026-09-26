package com.stubu.specdriven.holiday;

import com.stubu.specdriven.base.TimestampListener;
import com.stubu.specdriven.base.Timestamped;
import com.stubu.specdriven.region.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/** A public holiday of one region. Informational only: it never changes worked time. */
@Entity
@EntityListeners(TimestampListener.class)
@Table(name = "public_holiday", uniqueConstraints = @UniqueConstraint(name = "uq_public_holiday_region_date",
        columnNames = { "region_id", "holiday_date" }))
public class PublicHoliday implements Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The region the holiday belongs to; it never changes. */
    @Column(name = "region_id", nullable = false, updatable = false)
    private Long regionId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String name;

    /** Counts the changes; a save based on an older version is refused. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PublicHoliday() {
    }

    /** A holiday of the region "Default", where all holidays were before there were regions. */
    public PublicHoliday(LocalDate date, String name) {
        this(Region.DEFAULT_ID, date, name);
    }

    public PublicHoliday(long regionId, LocalDate date, String name) {
        this.regionId = regionId;
        this.date = date;
        this.name = name;
    }

    @Override
    public void stampCreated(Instant now) {
        createdAt = now;
    }

    public Long getVersion() {
        return version;
    }

    public Long getId() {
        return id;
    }

    public Long getRegionId() {
        return regionId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getName() {
        return name;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setName(String name) {
        this.name = name;
    }
}
