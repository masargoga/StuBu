package com.stubu.specdriven.employee;

import org.springframework.stereotype.Component;

/** The rule behind every administrative operation: the acting user is an active administrator. */
@Component
public class AdminAccess {

    private final EmployeeRepository employees;

    public AdminAccess(EmployeeRepository employees) {
        this.employees = employees;
    }

    /**
     * Checks that the user is an active administrator.
     *
     * @throws AdminOnlyException if the user does not exist, is inactive or is not an administrator
     */
    public void require(long userId) {
        Employee user = employees.findById(userId).orElseThrow(AdminOnlyException::new);
        if (!user.isActive() || user.getRole() != Role.ADMIN) {
            throw new AdminOnlyException();
        }
    }
}
