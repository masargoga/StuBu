package com.stubu.specdriven.base;

import com.stubu.specdriven.settings.AppLanguage;
import com.stubu.specdriven.settings.EmployeeSettingsService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * The dropdown with which the user chooses the language of the application (UC-016): four languages, each with its
 * flag and its name written in that language. The choice applies at once, is remembered in the browser and, for a
 * signed-in employee, in their settings.
 */
public class LanguageSelector extends Select<AppLanguage> implements LocaleChangeObserver {

    /** The cookie in which the browser remembers the chosen language. */
    public static final String COOKIE = "stubu-language";

    private static final Logger log = LoggerFactory.getLogger(LanguageSelector.class);

    private final transient EmployeeSettingsService settings;
    private final Long employeeId;
    private boolean updating;

    /**
     * @param settings   where a signed-in employee's choice is stored
     * @param employeeId the signed-in employee, or {@code null} on the login page (the choice then stays in the browser)
     */
    public LanguageSelector(EmployeeSettingsService settings, Long employeeId) {
        this.settings = settings;
        this.employeeId = employeeId;
        addClassName("language-selector");
        setTestId("language-select");
        setRenderer(new ComponentRenderer<>(LanguageSelector::option));
        setItems(AppLanguage.values());
        addValueChangeListener(event -> {
            if (!updating && event.getValue() != null) {
                chosen(event.getValue());
            }
        });
    }

    /** A language as the user sees it in the list and in the closed field: the flag and the name. */
    private static Span option(AppLanguage language) {
        Span flag = new Span();
        flag.addClassNames("flag", "flag-" + language.flag());
        flag.getElement().setAttribute("aria-hidden", "true");
        Span name = new Span(language.nativeName());
        name.addClassName("language-name");
        name.getElement().setAttribute("lang", language.code());
        Span option = new Span(flag, name);
        option.addClassName("language-option");
        return option;
    }

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        showCurrentLanguage();
        setAriaLabel(getTranslation("language.label"));
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        showCurrentLanguage();
        setAriaLabel(getTranslation("language.label"));
    }

    /** Shows the language of the page without treating it as a choice of the user. */
    private void showCurrentLanguage() {
        updating = true;
        try {
            setValue(AppLanguage.fromLocale(getLocale()).orElse(AppLanguage.DEFAULT));
        } finally {
            updating = false;
        }
    }

    private void chosen(AppLanguage language) {
        UI ui = UI.getCurrent();
        if (ui == null || AppLanguage.fromLocale(ui.getLocale()).orElse(AppLanguage.DEFAULT) == language) {
            return; // the language that is shown already: nothing changes and nothing is stored
        }
        ui.setLocale(language.locale());
        rememberInBrowser(ui, language);
        if (employeeId != null) {
            try {
                settings.saveLanguage(employeeId, language);
            } catch (DataAccessException e) {
                log.error("Saving the language {} of employee {} failed", language.code(), employeeId, e);
                Notification notification = Notification.show(getTranslation("language.saveFailed"), 8000,
                        Notification.Position.BOTTOM_START);
                notification.addThemeVariants(NotificationVariant.ERROR);
            }
        }
    }

    /** Remembers the language in the browser and tells the page its language. */
    public static void rememberInBrowser(UI ui, AppLanguage language) {
        ui.getPage().executeJs("""
                document.cookie = $0 + '=' + $1 + '; path=/; max-age=31536000; SameSite=Lax'
                    + (location.protocol === 'https:' ? '; Secure' : '');
                document.documentElement.lang = $1;""", COOKIE, language.code());
    }
}
