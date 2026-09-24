package com.stubu.specdriven.testsupport;

import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.security.EmployeePrincipal;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

final class WithEmployeeSecurityContextFactory implements WithSecurityContextFactory<WithEmployee> {

    @Override
    public SecurityContext createSecurityContext(WithEmployee annotation) {
        Employee employee = new Employee(annotation.email(), annotation.firstName(), annotation.lastName(),
                annotation.role(), 1L);
        OidcIdToken idToken = new OidcIdToken("test-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "sub-" + annotation.email(), "email", annotation.email()));
        EmployeePrincipal principal = new EmployeePrincipal(employee, idToken);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "mock"));
        return context;
    }
}
