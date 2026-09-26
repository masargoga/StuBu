package com.stubu.specdriven.settings;

import java.util.Locale;
import java.util.Optional;

/**
 * The languages the application is available in (UC-016). Each has the code that is stored and used in the texts
 * files, its name written in that language, and the flag that is shown next to it.
 */
public enum AppLanguage {

    ENGLISH("en", "English", "gb"),
    GERMAN("de", "Deutsch", "de"),
    SPANISH("es", "Español", "es"),
    FRENCH("fr", "Français", "fr");

    /** The language used when nothing else applies. */
    public static final AppLanguage DEFAULT = ENGLISH;

    private final String code;
    private final String nativeName;
    private final String flag;

    AppLanguage(String code, String nativeName, String flag) {
        this.code = code;
        this.nativeName = nativeName;
        this.flag = flag;
    }

    /** The language code, as stored (en, de, es, fr). */
    public String code() {
        return code;
    }

    /** The name of the language in that language. */
    public String nativeName() {
        return nativeName;
    }

    /** The name of the flag image (icons/flags/{flag}.svg). */
    public String flag() {
        return flag;
    }

    public Locale locale() {
        return Locale.forLanguageTag(code);
    }

    /** The language with this code, if it is one of the four; not case-sensitive. */
    public static Optional<AppLanguage> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (AppLanguage language : values()) {
            if (language.code.equalsIgnoreCase(code.strip())) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /** The language of a locale (regions such as de-AT or fr-CA count as their language), if it is one of the four. */
    public static Optional<AppLanguage> fromLocale(Locale locale) {
        return locale == null ? Optional.empty() : fromCode(locale.getLanguage());
    }
}
