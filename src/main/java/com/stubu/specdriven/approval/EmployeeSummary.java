package com.stubu.specdriven.approval;

import com.stubu.specdriven.employee.Role;

/** An employee a manager may look at. */
public record EmployeeSummary(long id, String fullName, String email, Role role, boolean active) {
}
