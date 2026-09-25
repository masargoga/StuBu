package com.stubu.specdriven.employee;

/**
 * What decides whether a signed-in employee may keep using the application: whether they are still active and
 * which role they have now.
 */
public record EmployeeAccess(boolean active, Role role) {
}
