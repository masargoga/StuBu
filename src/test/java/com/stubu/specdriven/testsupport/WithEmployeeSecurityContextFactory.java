package com.stubu.specdriven.testsupport;

import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.security.EmployeePrincipal;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

final class WithEmployeeSecurityContextFactory implements WithSecurityContextFactory<WithEmployee> {

    @Autowired(required = false)
    private EmployeeRepository employees;
    @Autowired(required = false)
    private DepartmentRepository departments;

    @Override
    public SecurityContext createSecurityContext(WithEmployee annotation) {
        Employee employee = employees != null && departments != null ? persisted(annotation) : synthetic(annotation, 1L);
        OidcIdToken idToken = new OidcIdToken("test-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "sub-" + annotation.email(), "email", annotation.email()));
        EmployeePrincipal principal = new EmployeePrincipal(employee, idToken);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "mock"));
        return context;
    }

    /** The employee as stored in the database (created on first use), so the principal has a real id. */
    private Employee persisted(WithEmployee annotation) {
        return employees.findByEmailIgnoreCase(annotation.email()).orElseGet(() -> {
            Department department = departments.findAll().stream().findFirst()
                    .orElseGet(() -> departments.save(new Department("Engineering")));
            return employees.save(synthetic(annotation, department.getId()));
        });
    }

    private static Employee synthetic(WithEmployee annotation, Long departmentId) {
        return new Employee(annotation.email(), annotation.firstName(), annotation.lastName(), annotation.role(),
                departmentId);
    }
}
