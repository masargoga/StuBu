package com.stubu.specdriven.base;

import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.settings.AppLanguage;
import com.stubu.specdriven.settings.EmployeeSettingsService;
import com.stubu.specdriven.settings.LanguageResolver;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import jakarta.servlet.http.Cookie;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Sets the language of every page when it opens (UC-016): the signed-in employee's stored language, else the language
 * chosen in the browser, else the language of the browser if it is one of the four, else English (see
 * {@link LanguageResolver}). It also tells the browser the language of the page (the {@code lang} attribute of the html
 * element, for screen readers and translation, WCAG 3.1.1) and lets the browser remember the stored language, so that
 * the login page uses it too.
 */
@Component
public class LanguageInitListener implements VaadinServiceInitListener {

    private static final Logger log = LoggerFactory.getLogger(LanguageInitListener.class);

    private final EmployeeSettingsService settings;

    public LanguageInitListener(EmployeeSettingsService settings) {
        this.settings = settings;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(initEvent -> apply(initEvent.getUI(), VaadinService.getCurrentRequest()));
    }

    /** Chooses and sets the language of a page that opens. */
    public void apply(UI ui, VaadinRequest request) {
        String chosen = cookie(request);
        String stored = null;
        Long employeeId = signedInEmployee();
        if (employeeId != null) {
            try {
                stored = settings.languageOf(employeeId).map(AppLanguage::code).orElse(null);
            } catch (DataAccessException e) {
                log.error("Could not read the language of employee {}, using the browser's", employeeId, e);
            }
        }
        List<Locale> browser = request == null ? List.of() : Collections.list(request.getLocales());
        AppLanguage language = LanguageResolver.resolve(stored, chosen, browser);
        ui.setLocale(language.locale());
        if (stored != null && !stored.equals(chosen)) {
            LanguageSelector.rememberInBrowser(ui, language); // the stored choice wins; the login page will know it too
        } else {
            ui.getPage().executeJs("document.documentElement.lang = $0", language.code());
        }
    }

    private static String cookie(VaadinRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (LanguageSelector.COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static Long signedInEmployee() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof EmployeePrincipal principal
                ? principal.getEmployeeId() : null;
    }
}
