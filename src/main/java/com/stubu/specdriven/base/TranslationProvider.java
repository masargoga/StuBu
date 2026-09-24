package com.stubu.specdriven.base;

import com.vaadin.flow.i18n.I18NProvider;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loads the UI texts from {@code vaadin-i18n/translations*.properties}: English (the default file) and
 * German. Unlike Vaadin's default provider it never falls back to the server's JVM locale, so an
 * English browser gets English even on a server that runs with a German default locale.
 */
@Component
public class TranslationProvider implements I18NProvider {

    private static final Logger log = LoggerFactory.getLogger(TranslationProvider.class);
    private static final String BUNDLE = "vaadin-i18n/translations";
    private static final ResourceBundle.Control NO_JVM_LOCALE_FALLBACK = ResourceBundle.Control
            .getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    /** The first locale is the default for browsers that ask for anything else. */
    private static final List<Locale> PROVIDED_LOCALES = List.of(Locale.ENGLISH, Locale.GERMAN);

    @Override
    public List<Locale> getProvidedLocales() {
        return PROVIDED_LOCALES;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        if (key == null) {
            return "";
        }
        try {
            String text = ResourceBundle.getBundle(BUNDLE, locale == null ? Locale.ENGLISH : locale,
                    NO_JVM_LOCALE_FALLBACK).getString(key);
            return params == null || params.length == 0 ? text : new MessageFormat(text, locale).format(params);
        } catch (MissingResourceException e) {
            log.warn("Missing translation for key '{}' and locale '{}'", key, locale);
            return "!" + key;
        }
    }
}
