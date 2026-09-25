package com.stubu.specdriven.approval;

import com.stubu.specdriven.employee.Employee;
import org.springframework.stereotype.Component;

/**
 * Who may review whose timesheet. The rules live here, independent of any page: a manager reviews the
 * timesheets of their direct reports and of the employees in their own department, an administrator those of
 * everybody, and nobody reviews their own. Inactive users have no access at all.
 */
@Component
public class ReviewerAuthorization {

    public boolean mayReview(Employee reviewer, Employee subject) {
        if (!reviewer.isActive() || reviewer.getId().equals(subject.getId())) {
            return false;
        }
        return switch (reviewer.getRole()) {
            case ADMIN -> true;
            case MANAGER -> isDirectReport(reviewer, subject) || isInSameDepartment(reviewer, subject);
            case EMPLOYEE -> false;
        };
    }

    static boolean isDirectReport(Employee manager, Employee employee) {
        return manager.getId().equals(employee.getManagerId());
    }

    static boolean isInSameDepartment(Employee manager, Employee employee) {
        return manager.getDepartmentId().equals(employee.getDepartmentId());
    }
}
