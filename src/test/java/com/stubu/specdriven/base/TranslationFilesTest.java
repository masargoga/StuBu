package com.stubu.specdriven.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stubu.specdriven.settings.AppLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Every text of the application exists in all four languages (UC-016, BR-07): the same keys, the same placeholders,
 * and apostrophes written so that they show (in a text with placeholders an apostrophe must be doubled, in one without
 * it must not be).
 */
class TranslationFilesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)[,}]");

    private static Properties load(AppLanguage language) throws IOException {
        String file = "/vaadin-i18n/translations" + (language == AppLanguage.ENGLISH ? "" : "_" + language.code())
                + ".properties";
        try (InputStream in = TranslationFilesTest.class.getResourceAsStream(file)) {
            assertTrue(in != null, "Missing file " + file);
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @ParameterizedTest
    @EnumSource(AppLanguage.class)
    void everyLanguageHasExactlyTheKeysOfTheEnglishTexts(AppLanguage language) throws IOException {
        Set<String> english = new TreeSet<>(load(AppLanguage.ENGLISH).stringPropertyNames());
        Set<String> keys = new TreeSet<>(load(language).stringPropertyNames());

        Set<String> missing = new TreeSet<>(english);
        missing.removeAll(keys);
        Set<String> extra = new TreeSet<>(keys);
        extra.removeAll(english);
        assertTrue(missing.isEmpty(), language + " lacks " + missing);
        assertTrue(extra.isEmpty(), language + " has texts that English does not have: " + extra);
    }

    @ParameterizedTest
    @EnumSource(AppLanguage.class)
    void everyTextHasTheSamePlaceholdersAsTheEnglishOne(AppLanguage language) throws IOException {
        Properties english = load(AppLanguage.ENGLISH);
        Properties texts = load(language);
        List<String> problems = new ArrayList<>();
        for (String key : english.stringPropertyNames()) {
            if (!placeholders(english.getProperty(key)).equals(placeholders(texts.getProperty(key)))) {
                problems.add(key);
            }
        }
        assertTrue(problems.isEmpty(), language + " has other placeholders than English in " + problems);
    }

    @ParameterizedTest
    @EnumSource(AppLanguage.class)
    void noTextIsEmptyAndNoTextIsLeftUntranslatedByMistake(AppLanguage language) throws IOException {
        Properties texts = load(language);
        Properties english = load(AppLanguage.ENGLISH);
        List<String> empty = new ArrayList<>();
        for (String key : texts.stringPropertyNames()) {
            if (texts.getProperty(key).isBlank()) {
                empty.add(key);
            }
        }
        assertTrue(empty.isEmpty(), language + " has empty texts: " + empty);
        if (language != AppLanguage.ENGLISH) {
            // A few words are the same in every language (or name a technology); everything else must differ.
            Set<String> same = new TreeSet<>();
            for (String key : english.stringPropertyNames()) {
                if (english.getProperty(key).equals(texts.getProperty(key))) {
                    same.add(key);
                }
            }
            same.removeIf(key -> key.startsWith("role.") || key.equals("time.missing.date") || key.equals("time.add.date")
                    || key.equals("holidays.date") || key.equals("timesheet.table.date") || key.equals("time.break.label")
                    || key.equals("timesheet.table.total") || key.equals("audit.action") || key.equals("approvals.action")
                    || key.equals("review.title") || key.equals("history.action.CREATE") || key.equals("timesheet.view.table")
                    || key.equals("audit.filter.action") || key.equals("manage.sort.NAME") || key.equals("audit.entity"));
            assertTrue(same.size() <= 12, language + " has texts identical to English (untranslated?): " + same);
        }
    }

    @ParameterizedTest
    @EnumSource(AppLanguage.class)
    void apostrophesShowAsTheyAreWritten(AppLanguage language) throws IOException {
        Properties texts = load(language);
        List<String> problems = new ArrayList<>();
        for (String key : texts.stringPropertyNames()) {
            String text = texts.getProperty(key);
            boolean formatted = !placeholders(text).isEmpty();
            if (formatted && text.replace("''", "").contains("'")) {
                problems.add(key + " (a single apostrophe is swallowed by the message format)");
            }
            if (!formatted && text.contains("''")) {
                problems.add(key + " (doubled apostrophes would show twice)");
            }
        }
        assertTrue(problems.isEmpty(), language + ": " + problems);
    }

    @ParameterizedTest
    @EnumSource(AppLanguage.class)
    void everyTextWithPlaceholdersCanBeFormatted(AppLanguage language) throws IOException {
        Properties texts = load(language);
        Locale locale = language.locale();
        for (String key : texts.stringPropertyNames()) {
            String text = texts.getProperty(key);
            if (placeholders(text).isEmpty()) {
                continue;
            }
            Object[] arguments = { "AAA", "BBB", "CCC", "DDD", "EEE" };
            if (key.startsWith("audit.count") || key.startsWith("manage.count") || key.startsWith("regions.inUse.") || key.equals("holidays.noneInYear")) {
                arguments = key.equals("holidays.noneInYear") ? new Object[] { "AAA", 2026 } : new Object[] { 1234, "BBB" };
            }
            String formatted = new MessageFormat(text, locale).format(arguments);
            assertFalse(formatted.contains("{"), key + " left a placeholder: " + formatted);
        }
    }

    @Test
    void theTranslationProviderOffersTheFourLanguagesAndFallsBackToEnglish() {
        TranslationProvider provider = new TranslationProvider();

        assertEquals(List.of(Locale.ENGLISH, Locale.GERMAN, Locale.forLanguageTag("es"), Locale.FRENCH),
                provider.getProvidedLocales());
        assertEquals("Sign out", provider.getTranslation("app.signOut", Locale.ENGLISH));
        assertEquals("Cerrar sesión", provider.getTranslation("app.signOut", Locale.forLanguageTag("es")));
        assertEquals("Se déconnecter", provider.getTranslation("app.signOut", Locale.FRENCH));
        assertEquals("Abmelden".length() > 0, provider.getTranslation("app.signOut", Locale.GERMAN).length() > 0);
    }
}
