package com.stubu.specdriven.security;

import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.base.LanguageSelector;
import com.stubu.specdriven.settings.AppLanguage;
import com.stubu.specdriven.settings.EmployeeSettingsService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** Audits the successful login, then sends the user to the page they asked for, or to their home dashboard. */
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final AuditService auditService;
    private final EmployeeSettingsService settings;

    public LoginSuccessHandler(AuditService auditService, EmployeeSettingsService settings) {
        this.auditService = auditService;
        this.settings = settings;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof EmployeePrincipal principal) {
            auditService.recordLoginSuccess(principal.getEmployeeId());
            adoptLanguageChosenInTheBrowser(request, principal.getEmployeeId());
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * A language chosen on the login page is kept in the employee's settings if they have none yet (UC-016, AF-2). A
     * language that is already stored wins. Failing to store it never stops the login.
     */
    private void adoptLanguageChosenInTheBrowser(HttpServletRequest request, Long employeeId) {
        if (request.getCookies() == null) {
            return;
        }
        for (Cookie cookie : request.getCookies()) {
            if (LanguageSelector.COOKIE.equals(cookie.getName())) {
                AppLanguage.fromCode(cookie.getValue()).ifPresent(language -> {
                    try {
                        if (settings.languageOf(employeeId).isEmpty()) {
                            settings.saveLanguage(employeeId, language);
                        }
                    } catch (DataAccessException e) {
                        log.error("Could not store the language {} chosen before the login of employee {}", language.code(), employeeId, e);
                    }
                });
            }
        }
    }
}
