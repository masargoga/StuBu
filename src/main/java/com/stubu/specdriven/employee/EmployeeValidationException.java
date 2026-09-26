package com.stubu.specdriven.employee;

import java.util.Set;

/** The employee data cannot be saved; every problem found is listed. */
public class EmployeeValidationException extends RuntimeException {

    public enum Problem {
        EMAIL_INVALID, EMAIL_EXISTS, FIRST_NAME_REQUIRED, LAST_NAME_REQUIRED, ROLE_REQUIRED, DEPARTMENT_REQUIRED,
        DEPARTMENT_UNKNOWN, REGION_REQUIRED, REGION_UNKNOWN, MANAGER_UNKNOWN, MANAGER_IS_SELF, MANAGER_CYCLE, TOO_LONG, LAST_ADMIN
    }

    private final Set<Problem> problems;

    public EmployeeValidationException(Set<Problem> problems) {
        super("Invalid employee data: " + problems);
        this.problems = Set.copyOf(problems);
    }

    public Set<Problem> getProblems() {
        return problems;
    }

    public boolean has(Problem problem) {
        return problems.contains(problem);
    }
}
