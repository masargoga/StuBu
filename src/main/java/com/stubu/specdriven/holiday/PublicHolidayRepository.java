package com.stubu.specdriven.holiday;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    List<PublicHoliday> findByDateBetweenOrderByDate(LocalDate from, LocalDate to);
}
