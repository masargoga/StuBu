package com.stubu.specdriven.employee;

/** An employee as the administrator's list shows them. {@code managerName} is {@code null} without a manager. */
public record EmployeeRow(long id, String email, String firstName, String lastName, Role role, long departmentId,
        String departmentName, Long managerId, String managerName, boolean active) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
