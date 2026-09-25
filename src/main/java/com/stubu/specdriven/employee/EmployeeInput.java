package com.stubu.specdriven.employee;

/**
 * The data of the employee form. On editing, the email is ignored: it never changes once the employee exists.
 *
 * @param managerId       the employee's manager, or {@code null} for none
 * @param expectedVersion on editing, the version of the employee the form was opened with: the change is refused
 *                        when somebody else changed the employee since; {@code null} skips the check
 */
public record EmployeeInput(String email, String firstName, String lastName, Role role, Long managerId,
        Long departmentId, Long expectedVersion) {

    public EmployeeInput(String email, String firstName, String lastName, Role role, Long managerId,
            Long departmentId) {
        this(email, firstName, lastName, role, managerId, departmentId, null);
    }
}
