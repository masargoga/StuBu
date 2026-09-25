package com.stubu.specdriven.employee;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditJson;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.employee.EmployeeValidationException.Problem;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates, changes and deactivates employees (UC-011). Only administrators may do this; every operation acts for the
 * administrator it is given, which callers take from the authenticated user. A change and its audit entry (with old
 * and new values) are stored in one transaction. Employees are never deleted: deactivating keeps their history.
 */
@Service
public class EmployeeAdminService {

    static final String ENTITY_TYPE = "Employee";
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final AuditService auditService;
    private final TransactionTemplate transaction;

    public EmployeeAdminService(EmployeeRepository employees, DepartmentRepository departments,
            AuditService auditService, PlatformTransactionManager transactionManager) {
        this.employees = employees;
        this.departments = departments;
        this.auditService = auditService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** All employees, active and inactive, sorted by name. */
    @Transactional(readOnly = true)
    public List<EmployeeRow> list(long adminId) {
        requireAdmin(adminId);
        Map<Long, Employee> byId = employees.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<Long, String> departmentNames = departments.findAll().stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return byId.values().stream()
                .sorted(Comparator.comparing(Employee::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Employee::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(employee -> row(employee, departmentNames, byId)).toList();
    }

    /** The departments to choose from. */
    @Transactional(readOnly = true)
    public List<Choice> departments(long adminId) {
        requireAdmin(adminId);
        return departments.findAll().stream().sorted(Comparator.comparing(Department::getName,
                String.CASE_INSENSITIVE_ORDER)).map(department -> new Choice(department.getId(), department.getName()))
                .toList();
    }

    /** The active managers and administrators who can be somebody's manager. */
    @Transactional(readOnly = true)
    public List<Choice> managerCandidates(long adminId) {
        requireAdmin(adminId);
        return employees.findAll().stream().filter(Employee::isActive)
                .filter(employee -> employee.getRole() != Role.EMPLOYEE)
                .sorted(Comparator.comparing(Employee::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Employee::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(employee -> new Choice(employee.getId(), employee.getFullName())).toList();
    }

    /**
     * Creates an active employee.
     *
     * @throws AdminOnlyException          if the acting user is not an active administrator
     * @throws EmployeeValidationException if the data is not acceptable, with every problem found
     */
    public EmployeeRow create(long adminId, EmployeeInput input) {
        requireAdmin(adminId);
        Set<Problem> problems = EnumSet.noneOf(Problem.class);
        String email = Employee.normalizeEmail(input.email());
        validateEmail(email, problems);
        validateFields(input, null, problems);
        if (email != null && problems.isEmpty() && employees.findByEmailIgnoreCase(email).isPresent()) {
            problems.add(Problem.EMAIL_EXISTS);
        }
        if (!problems.isEmpty()) {
            throw new EmployeeValidationException(problems);
        }
        try {
            Employee saved = transaction.execute(status -> {
                Employee employee = new Employee(email, input.firstName().strip(), input.lastName().strip(),
                        input.role(), input.departmentId());
                employee.setManagerId(input.managerId());
                Employee stored = employees.saveAndFlush(employee);
                auditService.record(adminId, ENTITY_TYPE, stored.getId(), AuditAction.CREATE, null, values(stored), null);
                return stored;
            });
            return toRow(Objects.requireNonNull(saved));
        } catch (DataIntegrityViolationException e) {
            // Somebody else created the same email at the same moment (the unique constraint decides).
            throw new EmployeeValidationException(EnumSet.of(Problem.EMAIL_EXISTS));
        }
    }

    /**
     * Changes an employee's name, role, manager and department. The email cannot be changed. Nothing is written
     * when nothing changed.
     *
     * @throws EmployeeNotFoundException   if there is no such employee
     * @throws EmployeeValidationException if the data is not acceptable
     */
    public EmployeeRow update(long adminId, long employeeId, EmployeeInput input) {
        requireAdmin(adminId);
        Employee current = employees.findById(employeeId).orElseThrow(EmployeeNotFoundException::new);
        Set<Problem> problems = EnumSet.noneOf(Problem.class);
        validateFields(input, current, problems);
        if (problems.isEmpty() && current.getRole() == Role.ADMIN && current.isActive()
                && input.role() != Role.ADMIN && isLastActiveAdmin(current)) {
            problems.add(Problem.LAST_ADMIN);
        }
        if (!problems.isEmpty()) {
            throw new EmployeeValidationException(problems);
        }
        return toRow(Objects.requireNonNull(transaction.execute(status -> {
            Employee employee = employees.findById(employeeId).orElseThrow(EmployeeNotFoundException::new);
            String before = values(employee);
            employee.setFirstName(input.firstName().strip());
            employee.setLastName(input.lastName().strip());
            employee.setRole(input.role());
            employee.setManagerId(input.managerId());
            employee.setDepartmentId(input.departmentId());
            String after = values(employee);
            if (!before.equals(after)) {
                Employee stored = employees.saveAndFlush(employee);
                auditService.record(adminId, ENTITY_TYPE, stored.getId(), AuditAction.UPDATE, before, after, null);
            }
            return employee;
        })));
    }

    /**
     * Deactivates an employee: they can no longer sign in, and their history stays.
     *
     * @param reason why; optional, kept in the audit log
     * @throws EmployeeNotFoundException     if there is no such employee
     * @throws DeactivationRefusedException  for oneself, the last administrator, or an inactive employee
     */
    public void deactivate(long adminId, long employeeId, String reason) {
        requireAdmin(adminId);
        Employee current = employees.findById(employeeId).orElseThrow(EmployeeNotFoundException::new);
        if (current.getId().equals(adminId)) {
            throw new DeactivationRefusedException(DeactivationRefusedException.Reason.SELF);
        }
        if (!current.isActive()) {
            throw new DeactivationRefusedException(DeactivationRefusedException.Reason.ALREADY_INACTIVE);
        }
        if (current.getRole() == Role.ADMIN && isLastActiveAdmin(current)) {
            throw new DeactivationRefusedException(DeactivationRefusedException.Reason.LAST_ADMIN);
        }
        transaction.executeWithoutResult(status -> {
            Employee employee = employees.findById(employeeId).orElseThrow(EmployeeNotFoundException::new);
            String before = values(employee);
            employee.setActive(false);
            employees.saveAndFlush(employee);
            auditService.record(adminId, ENTITY_TYPE, employeeId, AuditAction.UPDATE, before, values(employee),
                    reason == null || reason.isBlank() ? "Deactivated" : "Deactivated: " + reason.strip());
        });
    }

    // --- validation --------------------------------------------------------------------------------

    private void validateEmail(String email, Set<Problem> problems) {
        if (email == null || email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
            problems.add(Problem.EMAIL_INVALID);
        }
    }

    /** The checks shared by create and update; {@code existing} is the employee being edited, if any. */
    private void validateFields(EmployeeInput input, Employee existing, Set<Problem> problems) {
        if (input.firstName() == null || input.firstName().isBlank()) {
            problems.add(Problem.FIRST_NAME_REQUIRED);
        } else if (input.firstName().strip().length() > MAX_NAME_LENGTH) {
            problems.add(Problem.TOO_LONG);
        }
        if (input.lastName() == null || input.lastName().isBlank()) {
            problems.add(Problem.LAST_NAME_REQUIRED);
        } else if (input.lastName().strip().length() > MAX_NAME_LENGTH) {
            problems.add(Problem.TOO_LONG);
        }
        if (input.role() == null) {
            problems.add(Problem.ROLE_REQUIRED);
        }
        if (input.departmentId() == null) {
            problems.add(Problem.DEPARTMENT_REQUIRED);
        } else if (!departments.existsById(input.departmentId())) {
            problems.add(Problem.DEPARTMENT_UNKNOWN);
        }
        if (input.managerId() != null) {
            if (existing != null && input.managerId().equals(existing.getId())) {
                problems.add(Problem.MANAGER_IS_SELF);
            } else if (!employees.existsById(input.managerId())) {
                problems.add(Problem.MANAGER_UNKNOWN);
            } else if (existing != null && reportsUpTo(input.managerId(), existing.getId())) {
                problems.add(Problem.MANAGER_CYCLE);
            }
        }
    }

    /** Whether {@code employeeId} is somewhere above {@code startId} in the chain of managers. */
    private boolean reportsUpTo(long startId, long employeeId) {
        Set<Long> seen = new java.util.HashSet<>();
        Long next = startId;
        while (next != null && seen.add(next)) {
            if (next == employeeId) {
                return true;
            }
            next = employees.findById(next).map(Employee::getManagerId).orElse(null);
        }
        return false;
    }

    private boolean isLastActiveAdmin(Employee admin) {
        return employees.countByRoleAndActive(Role.ADMIN, true) <= 1 && admin.isActive();
    }

    private void requireAdmin(long adminId) {
        Employee admin = employees.findById(adminId).orElseThrow(AdminOnlyException::new);
        if (!admin.isActive() || admin.getRole() != Role.ADMIN) {
            throw new AdminOnlyException();
        }
    }

    // --- mapping -----------------------------------------------------------------------------------

    private EmployeeRow toRow(Employee employee) {
        Map<Long, String> departmentNames = new HashMap<>();
        departments.findById(employee.getDepartmentId())
                .ifPresent(department -> departmentNames.put(department.getId(), department.getName()));
        Map<Long, Employee> byId = new HashMap<>();
        if (employee.getManagerId() != null) {
            employees.findById(employee.getManagerId()).ifPresent(manager -> byId.put(manager.getId(), manager));
        }
        return row(employee, departmentNames, byId);
    }

    private static EmployeeRow row(Employee employee, Map<Long, String> departmentNames, Map<Long, Employee> byId) {
        Employee manager = employee.getManagerId() == null ? null : byId.get(employee.getManagerId());
        return new EmployeeRow(employee.getId(), employee.getEmail(), employee.getFirstName(), employee.getLastName(),
                employee.getRole(), employee.getDepartmentId(), departmentNames.get(employee.getDepartmentId()),
                employee.getManagerId(), manager == null ? null : manager.getFullName(), employee.isActive());
    }

    /** The audit values of an employee. */
    private static String values(Employee employee) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("email", employee.getEmail());
        values.put("firstName", employee.getFirstName());
        values.put("lastName", employee.getLastName());
        values.put("role", employee.getRole().name());
        values.put("managerId", employee.getManagerId());
        values.put("departmentId", employee.getDepartmentId());
        values.put("active", employee.isActive());
        return AuditJson.object(values);
    }
}
