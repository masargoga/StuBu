package com.stubu.specdriven.employee;

import java.util.List;

/**
 * Application role stored on the employee. Managers and administrators are also employees,
 * so their role implies the EMPLOYEE authority (they record their own time).
 */
public enum Role {
    EMPLOYEE, MANAGER, ADMIN;

    /** Role names without the Spring Security "ROLE_" prefix. */
    public List<String> grantedRoles() {
        return switch (this) {
            case EMPLOYEE -> List.of(EMPLOYEE.name());
            case MANAGER -> List.of(MANAGER.name(), EMPLOYEE.name());
            case ADMIN -> List.of(ADMIN.name(), EMPLOYEE.name());
        };
    }
}
