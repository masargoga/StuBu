package com.stubu.specdriven.employee;

import java.time.Instant;

/**
 * An employee as the administrator's list shows them.
 *
 * @param managerName {@code null} without a manager
 * @param lastLoginAt the latest successful login, or {@code null} if the employee never signed in
 * @param version     changes when the employee is changed; an edit refers to the version it started from
 */
public record EmployeeRow(long id, String email, String firstName, String lastName, Role role, long departmentId,
        String departmentName, Long managerId, String managerName, boolean active, Instant createdAt,
        Instant lastLoginAt, long version) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
