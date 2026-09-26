package com.stubu.specdriven.settings;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeSettingRepository extends JpaRepository<EmployeeSetting, Long> {

    /** The stored language code of the employee with this email address (not case-sensitive), if there is one. */
    @Query("select s.language from EmployeeSetting s join com.stubu.specdriven.employee.Employee e "
            + "on e.id = s.employeeId where lower(e.email) = lower(:email) and s.language is not null")
    Optional<String> findLanguageByEmail(@Param("email") String email);
}
