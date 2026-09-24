package com.stubu.specdriven.employee;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository employees;

    public EmployeeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    /** Finds an employee by email regardless of the active flag. The email match is case-insensitive. */
    @Transactional(readOnly = true)
    public Optional<Employee> findByEmail(String email) {
        return employees.findByEmailIgnoreCase(Employee.normalizeEmail(email));
    }
}
