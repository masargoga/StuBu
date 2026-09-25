package com.stubu.specdriven.employee;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmailIgnoreCase(String email);

    List<Employee> findByManagerId(Long managerId);

    long countByRoleAndActive(Role role, boolean active);

    List<Employee> findByDepartmentId(Long departmentId);
}
