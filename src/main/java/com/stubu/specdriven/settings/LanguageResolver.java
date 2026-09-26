package com.stubu.specdriven.settings;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides which language a page is shown in when it opens (UC-016, BR-03): the signed-in employee's stored language,
 * else the language chosen in the browser, else the language of the browser if it is one of the four, else English.
 * A value that is not one of the four languages (another language, a changed cookie) is ignored.
 */
public final class LanguageResolver {

    private LanguageResolver() {
    }

    /**
     * @param stored  the language code in the employee's settings, or {@code null} (not signed in, or never chosen)
     * @param chosen  the language code remembered in the browser, or {@code null}
     * @param browser the languages the browser asks for, most wanted first
     */
    public static AppLanguage resolve(String stored, String chosen, List<Locale> browser) {
        Optional<AppLanguage> fromSettings = AppLanguage.fromCode(stored);
        if (fromSettings.isPresent()) {
            return fromSettings.get();
        }
        Optional<AppLanguage> fromBrowserChoice = AppLanguage.fromCode(chosen);
        if (fromBrowserChoice.isPresent()) {
            return fromBrowserChoice.get();
        }
        if (browser != null) {
            for (Locale locale : browser) {
                Optional<AppLanguage> supported = AppLanguage.fromLocale(locale);
                if (supported.isPresent()) {
                    return supported.get();
                }
            }
        }
        return AppLanguage.DEFAULT;
    }
}
