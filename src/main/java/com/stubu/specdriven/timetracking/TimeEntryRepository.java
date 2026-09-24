package com.stubu.specdriven.timetracking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    /** The employee's open work period, if they are currently working. */
    Optional<TimeEntry> findByOpenEmployeeId(Long employeeId);

    /** The employee's work periods that started in [from, to), earliest first. */
    @Query("""
            select t from TimeEntry t
            where t.employeeId = :employeeId and t.checkInAt >= :from and t.checkInAt < :to
            order by t.checkInAt asc""")
    List<TimeEntry> findStartedBetween(@Param("employeeId") Long employeeId, @Param("from") Instant from,
            @Param("to") Instant to);

    /** True if a work period of the employee overlaps the given period; an open period lasts until {@code now}. */
    @Query("""
            select count(t) > 0 from TimeEntry t
            where t.employeeId = :employeeId
              and t.checkInAt < :end
              and coalesce(t.checkOutAt, :now) > :start""")
    boolean overlaps(@Param("employeeId") Long employeeId, @Param("start") Instant start, @Param("end") Instant end,
            @Param("now") Instant now);
}
