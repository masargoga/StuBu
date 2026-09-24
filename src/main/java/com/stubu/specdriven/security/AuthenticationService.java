package com.stubu.specdriven.security;

import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeService;
import org.springframework.stereotype.Service;

/**
 * Application identity resolution: maps an authenticated email address to the application employee.
 * Knows nothing about the IAM protocol; it only receives an email address.
 */
@Service
public class AuthenticationService {

    private final EmployeeService employees;

    public AuthenticationService(EmployeeService employees) {
        this.employees = employees;
    }

    /**
     * Finds the active employee for the authenticated email address. An employee is never created
     * automatically from a successful IAM login.
     *
     * @throws LoginDeniedException if there is no employee for the email or the employee is inactive
     */
    public Employee resolveActiveEmployee(String email) {
        Employee employee = employees.findByEmail(email)
                .orElseThrow(() -> new LoginDeniedException(LoginDeniedException.Reason.NOT_REGISTERED, email, null));
        if (!employee.isActive()) {
            throw new LoginDeniedException(LoginDeniedException.Reason.INACTIVE, email, employee.getId());
        }
        return employee;
    }
}
