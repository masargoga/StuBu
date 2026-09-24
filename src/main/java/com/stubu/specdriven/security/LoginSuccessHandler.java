package com.stubu.specdriven.security;

import com.stubu.specdriven.audit.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Audits the successful login, then sends the user to the page they asked for, or to their home dashboard. */
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AuditService auditService;

    public LoginSuccessHandler(AuditService auditService) {
        this.auditService = auditService;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof EmployeePrincipal principal) {
            auditService.recordLoginSuccess(principal.getEmployeeId());
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
