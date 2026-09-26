package com.stubu.specdriven.holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    List<PublicHoliday> findByRegionIdAndDateBetweenOrderByDate(Long regionId, LocalDate from, LocalDate to);

    List<PublicHoliday> findAllByOrderByDateAsc();

    Optional<PublicHoliday> findByRegionIdAndDate(Long regionId, LocalDate date);

    long countByRegionId(Long regionId);

    /** How many holidays each region has: rows of region id and count. */
    @org.springframework.data.jpa.repository.Query("select h.regionId, count(h) from PublicHoliday h group by h.regionId")
    List<Object[]> countByRegion();
}
