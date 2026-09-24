package com.stubu.specdriven.testsupport;

import com.stubu.specdriven.employee.Role;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Runs a test as a signed-in employee, i.e. with the same {@code EmployeePrincipal} a real OIDC login
 * produces. The employee does not need to exist in the database.
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithEmployeeSecurityContextFactory.class)
public @interface WithEmployee {

    String firstName() default "Alice";

    String lastName() default "Employee";

    String email() default "alice.employee@example.com";

    Role role() default Role.EMPLOYEE;
}
