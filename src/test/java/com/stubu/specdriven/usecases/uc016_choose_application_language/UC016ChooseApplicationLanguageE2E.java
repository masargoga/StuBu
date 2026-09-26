package com.stubu.specdriven.usecases.uc016_choose_application_language;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UC-016 Choose Application Language in a real browser: the selector on the login page and in the header, switching
 * the language, remembering it (browser and settings), and the longer texts of the other languages at every screen
 * size. The behaviour behind it is covered by {@code UC016ChooseApplicationLanguage}.
 */
class UC016ChooseApplicationLanguageE2E extends E2ETest {

    @Autowired
    JdbcTemplate jdbc;

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    @BeforeEach
    void noLanguageChosenYet() {
        jdbc.update("delete from employee_setting");
    }

    private void choose(String nativeName) {
        page.getByTestId("language-select").click();
        page.locator("vaadin-select-list-box vaadin-select-item").filter(new Locator.FilterOptions().setHasText(nativeName)).first().click();
    }

    private static String code(String nativeName) {
        return switch (nativeName) {
            case "Deutsch" -> "de";
            case "Español" -> "es";
            default -> "fr";
        };
    }

    private String pageLanguage() {
        return (String) page.evaluate("document.documentElement.lang");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theSelectorShowsFlagsAndNamesOnTheLoginPageAndInTheHeader(Viewport viewport) {
        open(viewport);
        page.navigate(url("/login"));
        page.getByTestId("language-select").waitFor();

        assertThat(page.getByTestId("language-select")).isVisible();
        assertThat(page.locator("[data-testid=language-select] vaadin-select-value-button .flag")).isVisible();
        assertNoHorizontalOverflow();
        screenshot("login-" + viewport.name());
        page.getByTestId("language-select").click();
        List<String> names = page.locator("vaadin-select-list-box vaadin-select-item").allInnerTexts().stream().map(String::strip).toList();
        assertEquals(List.of("English", "Deutsch", "Español", "Français"), names);
        assertEquals(4, page.locator("vaadin-select-list-box .flag").count(), "Every entry has its flag");
        screenshot("login-open-" + viewport.name());
        page.keyboard().press("Escape");

        signInAsAlice();
        assertThat(page.getByTestId("language-select")).isVisible();
        assertNoHorizontalOverflow();
        if (viewport.width() <= 640) {
            assertThat(page.locator("[data-testid=language-select] vaadin-select-value-button .language-name")).not().isInViewport();
        } else {
            assertThat(page.locator("[data-testid=language-select] vaadin-select-value-button .language-name")).hasText("English");
        }
        assertThat(page.locator("[data-testid=language-select] vaadin-select-value-button .flag")).isVisible();
        screenshot("header-" + viewport.name());
    }

    @Test
    void choosingALanguageSwitchesThePageAndIsRememberedInTheBrowserAndTheSettings() {
        open(DESKTOP);
        signInAsAlice();
        assertEquals("en", pageLanguage());

        choose("Español");

        assertThat(page.locator(".home-welcome")).hasText("Bienvenido/a, Alice Employee");
        assertThat(page.getByTestId("check-in")).containsText("Registrar entrada");
        assertThat(page.getByTestId("nav-timesheet")).containsText("Mi hoja de horas");
        assertThat(page.getByTestId("sign-out")).hasText("Cerrar sesión");
        assertEquals("es", pageLanguage());
        assertThat(page.locator("[data-testid=language-select] vaadin-select-value-button .language-name")).hasText("Español");
        screenshot("today-es");
        assertEquals("es", jdbc.queryForObject("select s.language from employee_setting s join employee e on e.id = s.employee_id "
                + "where e.email = ?", String.class, ALICE));

        page.reload();
        assertThat(page.locator(".home-welcome")).hasText("Bienvenido/a, Alice Employee");
        assertEquals("es", pageLanguage());

        // Another browser (no cookie): the stored language follows the employee.
        open(DESKTOP);
        signInAsAlice();
        assertThat(page.locator(".home-welcome")).hasText("Bienvenido/a, Alice Employee");

        choose("Français");
        assertThat(page.locator(".home-welcome")).hasText("Bienvenue, Alice Employee");
        assertThat(page.getByTestId("check-in")).containsText("Pointer l'arrivée");
        screenshot("today-fr");

        // After signing out, the login page is in the language chosen in this browser.
        page.getByTestId("sign-out").click();
        assertThat(page.locator(".login-provider-button").first()).containsText("Se connecter avec");
        assertEquals("fr", pageLanguage());
    }

    @Test
    void aLanguageChosenOnTheLoginPageIsStoredAtSignInIfNothingWasStoredYet() {
        open(DESKTOP);
        page.navigate(url("/login"));
        page.getByTestId("language-select").waitFor();
        choose("Deutsch");
        assertThat(page.locator(".login-provider-button").first()).containsText("anmelden");
        assertEquals("de", pageLanguage());

        signIn(ALICE);

        assertThat(page.locator(".home-welcome")).hasText("Willkommen, Alice Employee");
        assertThat(page.locator("[data-testid=language-select] vaadin-select-value-button .language-name")).hasText("Deutsch");
        assertEquals("de", jdbc.queryForObject("select s.language from employee_setting s join employee e on e.id = s.employee_id "
                + "where e.email = ?", String.class, ALICE));
    }

    @Test
    void theBrowserLanguageIsUsedUntilTheEmployeeChoosesAndAnUnknownOneMeansEnglish() {
        open(DESKTOP, "fr-FR");
        page.navigate(url("/login"));
        assertThat(page.locator(".login-provider-button").first()).containsText("Se connecter avec");

        open(DESKTOP, "it-IT");
        page.navigate(url("/login"));
        assertThat(page.locator(".login-provider-button").first()).containsText("Sign in with");
        assertEquals("en", pageLanguage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theLongerTextsOfEveryLanguageFitEveryScreenSize(Viewport viewport) {
        for (String language : List.of("Deutsch", "Español", "Français")) {
            open(viewport);
            signInAsAlice();
            choose(language);
            page.waitForFunction("code => document.documentElement.lang === code", code(language));
            assertNoHorizontalOverflow();
            assertThat(page.getByTestId("sign-out")).isInViewport(new LocatorAssertions.IsInViewportOptions().setRatio(1));
            screenshot("today-" + language + "-" + viewport.name());

            page.navigate(url("/timesheet"));
            page.locator(".day-row").first().waitFor();
            assertNoHorizontalOverflow();
            assertReadable(".day-label");
            screenshot("month-" + language + "-" + viewport.name());
        }
    }
}
