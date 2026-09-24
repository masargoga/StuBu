package com.stubu.specdriven.security;

import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.Role;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** The application user context established after login: the OIDC identity plus the employee it maps to. */
public class EmployeePrincipal extends DefaultOidcUser {

    private final Long employeeId;
    private final String email;
    private final String fullName;
    private final Role role;

    public EmployeePrincipal(Employee employee, OidcIdToken idToken) {
        super(authorities(employee.getRole()), idToken);
        this.employeeId = employee.getId();
        this.email = employee.getEmail();
        this.fullName = employee.getFullName();
        this.role = employee.getRole();
    }

    static List<GrantedAuthority> authorities(Role role) {
        return role.grantedRoles().stream()
                .<GrantedAuthority>map(name -> new SimpleGrantedAuthority("ROLE_" + name))
                .toList();
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    /** The application identifies users by their employee email rather than the IAM subject. */
    @Override
    public String getName() {
        return email;
    }
}
