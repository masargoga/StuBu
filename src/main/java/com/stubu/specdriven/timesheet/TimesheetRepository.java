package com.stubu.specdriven.timesheet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimesheetRepository extends JpaRepository<Timesheet, Long> {

    Optional<Timesheet> findByEmployeeIdAndYearAndMonth(Long employeeId, int year, int month);

    List<Timesheet> findByEmployeeIdOrderByYearDescMonthDesc(Long employeeId);

    List<Timesheet> findByStatusAndEmployeeIdInOrderBySubmittedAtAsc(TimesheetStatus status,
            Collection<Long> employeeIds);
}
