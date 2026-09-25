package com.stubu.specdriven.employee;

/**
 * The data of the employee form. On editing, the email is ignored: it never changes once the employee exists.
 *
 * @param managerId the employee's manager, or {@code null} for none
 */
public record EmployeeInput(String email, String firstName, String lastName, Role role, Long managerId,
        Long departmentId) {
}
