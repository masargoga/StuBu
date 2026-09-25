package com.stubu.specdriven.employee;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmailIgnoreCase(String email);

    List<Employee> findByManagerId(Long managerId);

    long countByRoleAndActive(Role role, boolean active);

    /** The ids of the employees whose name or email address contains the text; {@code pattern} is lower case with % around; ! escapes % and _. */
    @org.springframework.data.jpa.repository.Query("select e.id from Employee e where lower(e.email) like :pattern escape '!' "
            + "or lower(e.firstName) like :pattern escape '!' or lower(e.lastName) like :pattern escape '!' "
            + "or lower(concat(e.firstName, ' ', e.lastName)) like :pattern escape '!'")
    List<Long> findIdsByText(@org.springframework.data.repository.query.Param("pattern") String pattern);

    List<Employee> findByDepartmentId(Long departmentId);

    /** Whether the employee is active and their current role; empty if the employee no longer exists. */
    @org.springframework.data.jpa.repository.Query("select new com.stubu.specdriven.employee.EmployeeAccess(e.active, e.role) from Employee e where e.id = :id")
    Optional<EmployeeAccess> findAccessById(@org.springframework.data.repository.query.Param("id") Long id);
}
