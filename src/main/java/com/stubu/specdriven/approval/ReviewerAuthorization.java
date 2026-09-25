package com.stubu.specdriven.approval;

import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.Role;
import org.springframework.stereotype.Component;

/**
 * Who may look at and decide about whose timesheet. The rules live here, independent of any page. A manager
 * looks at and decides about the timesheets of their direct reports and of the employees in their own department.
 * An administrator may look at everybody's timesheet but never decides about one: approval stays a manager's
 * responsibility (spec section 22). Nobody looks at or decides about their own, and inactive users have no access.
 */
@Component
public class ReviewerAuthorization {

    /** Whether the reviewer may see the subject's timesheets. */
    public boolean mayView(Employee reviewer, Employee subject) {
        if (!reviewer.isActive() || reviewer.getId().equals(subject.getId())) {
            return false;
        }
        return switch (reviewer.getRole()) {
            case ADMIN -> true;
            case MANAGER -> isDirectReport(reviewer, subject) || isInSameDepartment(reviewer, subject);
            case EMPLOYEE -> false;
        };
    }

    /** Whether the reviewer may approve or reject the subject's timesheets: managers only. */
    public boolean mayDecide(Employee reviewer, Employee subject) {
        return reviewer.getRole() == Role.MANAGER && mayView(reviewer, subject);
    }

    static boolean isDirectReport(Employee manager, Employee employee) {
        return manager.getId().equals(employee.getManagerId());
    }

    static boolean isInSameDepartment(Employee manager, Employee employee) {
        return manager.getDepartmentId().equals(employee.getDepartmentId());
    }
}
