package com.stubu.specdriven.testsupport;

import com.stubu.specdriven.security.EmployeePrincipal;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint that reveals the user context of the current session. */
@RestController
public class WhoAmIController {

    @GetMapping("/test/whoami")
    String whoAmI(Authentication authentication) {
        if (authentication.getPrincipal() instanceof EmployeePrincipal principal) {
            String roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .sorted()
                    .collect(Collectors.joining(","));
            return "employeeId=" + principal.getEmployeeId() + ";email=" + principal.getEmail() + ";roles=" + roles;
        }
        return "other:" + authentication.getName();
    }
}
