package com.stubu.specdriven.usecases.uc016_choose_application_language;

import static com.stubu.specdriven.testsupport.ViewTexts.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.base.LanguageInitListener;
import com.stubu.specdriven.base.LanguageSelector;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeInput;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.home.HomeView;
import com.stubu.specdriven.notification.EmailNotificationService;
import com.stubu.specdriven.notification.TimesheetSubmittedNotice;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.security.LoginSuccessHandler;
import com.stubu.specdriven.security.LoginView;
import com.stubu.specdriven.settings.AppLanguage;
import com.stubu.specdriven.settings.EmployeeSettingRepository;
import com.stubu.specdriven.settings.EmployeeSettingsService;
import com.stubu.specdriven.settings.LanguageResolver;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.VaadinRequest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * UC-016 Choose Application Language, as Alice. The selector is driven browserless; the language a page opens in
 * (the part that runs when a page is opened in a browser) is tested on the listener that decides it.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithEmployee
class UC016ChooseApplicationLanguage extends SpringBrowserlessTest {

    private static final String ALICE = "alice.employee@example.com";

    @Autowired
    EmployeeSettingsService service;
    @MockitoSpyBean
    EmployeeSettingRepository settings;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    LanguageInitListener initListener;
    @Autowired
    LoginSuccessHandler loginHandler;
    @Autowired
    EmailNotificationService emails;
    @MockitoBean
    JavaMailSender mailSender;

    private long alice;
    private int auditBefore;

    @BeforeEach
    void setUpScenario() {
        Mockito.reset(settings, mailSender);
        jdbc.update("delete from employee_setting");
        alice = employees.findByEmailIgnoreCase(ALICE).orElseThrow().getId();
        auditBefore = auditLog.findAllByOrderByIdAsc().size();
    }

    @AfterEach
    void reset() {
        Mockito.reset(settings, mailSender);
        SecurityContextHolder.clearContext();
        // The chosen languages must not leak into other test classes (emails and pages would come in Spanish).
        jdbc.update("delete from employee_setting");
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_theSelectorOffersTheFourLanguagesWithNameAndFlagAndShowsTheCurrentOne() {
        openHome(Locale.ENGLISH);

        LanguageSelector selector = find(LanguageSelector.class).single();
        assertEquals(List.of(AppLanguage.ENGLISH, AppLanguage.GERMAN, AppLanguage.SPANISH, AppLanguage.FRENCH),
                selector.getListDataView().getItems().toList());
        assertEquals(List.of("English", "Deutsch", "Español", "Français"),
                selector.getListDataView().getItems().map(AppLanguage::nativeName).toList());
        assertEquals(List.of("gb", "de", "es", "fr"), selector.getListDataView().getItems().map(AppLanguage::flag).toList());
        assertEquals(AppLanguage.ENGLISH, selector.getValue(), "The current language is selected");
        assertEquals("Language", selector.getAriaLabel().orElse(null));
    }

    @Test
    void mainFlow_choosingALanguageSwitchesThePageAtOnceAndStoresItInTheSettings() {
        openHome(Locale.ENGLISH);
        assertEquals("Welcome, Alice Employee", find(H2.class).single().getText());

        find(LanguageSelector.class).single().setValue(AppLanguage.SPANISH);

        assertEquals("es", UI.getCurrent().getLocale().getLanguage());
        assertEquals("Bienvenido/a, Alice Employee", find(H2.class).single().getText(), "The page switched at once");
        assertTrue(text(find(HomeView.class).single()).contains("Registrar entrada"), text(find(HomeView.class).single()));
        assertEquals("Idioma", find(LanguageSelector.class).single().getAriaLabel().orElse(null));
        assertEquals(AppLanguage.SPANISH, service.languageOf(alice).orElseThrow());
        assertEquals("es", jdbc.queryForObject("select language from employee_setting where employee_id = ?", String.class, alice));

        find(LanguageSelector.class).single().setValue(AppLanguage.FRENCH);
        assertEquals("Bienvenue, Alice Employee", find(H2.class).single().getText());
        assertEquals(AppLanguage.FRENCH, service.languageOf(alice).orElseThrow(), "The stored language is replaced");
        assertEquals(1, jdbc.queryForObject("select count(*) from employee_setting where employee_id = ?", Integer.class, alice),
                "One row per employee");
    }

    @Test
    void mainFlow_theLoginPageHasTheSelectorToo_andTheChoiceIsNotStoredBecauseNobodyIsKnown() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        navigate(LoginView.class);

        LanguageSelector selector = find(LanguageSelector.class).single();
        selector.setValue(AppLanguage.GERMAN);

        assertEquals("de", UI.getCurrent().getLocale().getLanguage());
        assertEquals(0, jdbc.queryForObject("select count(*) from employee_setting", Integer.class));
    }

    @Test
    void mainFlow_theLanguageOfAPageIsTheStoredOneThenTheBrowsersChoiceThenTheBrowsersLanguage() {
        assertEquals(AppLanguage.SPANISH, LanguageResolver.resolve("es", "fr", List.of(Locale.GERMAN)), "Stored wins");
        assertEquals(AppLanguage.FRENCH, LanguageResolver.resolve(null, "fr", List.of(Locale.GERMAN)), "Then the choice made in the browser");
        assertEquals(AppLanguage.GERMAN, LanguageResolver.resolve(null, null, List.of(Locale.GERMAN)), "Then the browser's language");
        assertEquals(AppLanguage.FRENCH, LanguageResolver.resolve(null, null, List.of(Locale.ITALIAN, Locale.CANADA_FRENCH)),
                "The first language of the browser that is offered; a region does not matter");
        assertEquals(AppLanguage.ENGLISH, LanguageResolver.resolve(null, null, List.of(Locale.ITALIAN)), "Then English");
        assertEquals(AppLanguage.ENGLISH, LanguageResolver.resolve(null, null, List.of()));
    }

    @Test
    void mainFlow_aPageOpensInTheStoredLanguageOfTheSignedInEmployee() {
        service.saveLanguage(alice, AppLanguage.FRENCH);
        signIn();
        UI ui = UI.getCurrent();

        initListener.apply(ui, request("es", Locale.GERMAN)); // the browser chose Spanish and asks for German

        assertEquals("fr", ui.getLocale().getLanguage(), "The stored language wins");
    }

    // --- AF-1 .. AF-4 ---------------------------------------------------------------------------

    @Test
    void af1_ifSavingFailsThePageStillSwitchesTheStoredLanguageStaysAndTheUserIsTold() {
        service.saveLanguage(alice, AppLanguage.GERMAN);
        openHome(Locale.GERMAN);
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(settings)
                .saveAndFlush(any());

        find(LanguageSelector.class).single().setValue(AppLanguage.SPANISH);

        assertEquals("es", UI.getCurrent().getLocale().getLanguage(), "The page switched anyway");
        Mockito.reset(settings);
        assertEquals(AppLanguage.GERMAN, service.languageOf(alice).orElseThrow(), "The stored language is unchanged");
        assertTrue(find(Notification.class).all().stream().anyMatch(notification -> notification.isOpened()),
                "The user is told");
        assertEquals("No se pudo guardar su idioma en sus ajustes. Solo se aplica en este dispositivo.",
                UI.getCurrent().getTranslation("language.saveFailed"));
    }

    @Test
    void af2_aLanguageChosenOnTheLoginPageIsStoredAtSignInOnlyIfNothingIsStoredYet() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(LanguageSelector.COOKIE, "fr"));

        loginHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), authentication());
        assertEquals(AppLanguage.FRENCH, service.languageOf(alice).orElseThrow(), "Nothing was stored: the choice is adopted");

        service.saveLanguage(alice, AppLanguage.GERMAN);
        request.setCookies(new Cookie(LanguageSelector.COOKIE, "es"));
        loginHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), authentication());
        assertEquals(AppLanguage.GERMAN, service.languageOf(alice).orElseThrow(), "What is stored wins");
    }

    @Test
    void af2_aFailureToStoreTheChoiceNeverStopsTheLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(LanguageSelector.COOKIE, "fr"));
        Mockito.doThrow(new DataAccessResourceFailureException("database is down")).when(settings).saveAndFlush(any());

        loginHandler.onAuthenticationSuccess(request, new MockHttpServletResponse(), authentication());

        Mockito.reset(settings);
        assertTrue(service.languageOf(alice).isEmpty());
    }

    @Test
    void af3_aLanguageThatIsNotOfferedIsIgnored() {
        signIn();
        UI ui = UI.getCurrent();

        initListener.apply(ui, request("it", Locale.ITALIAN));
        assertEquals("en", ui.getLocale().getLanguage(), "Italian is not offered: English");

        initListener.apply(ui, request("<script>", Locale.GERMAN));
        assertEquals("de", ui.getLocale().getLanguage(), "A changed cookie is ignored: the browser's language");

        jdbc.update("delete from employee_setting");
        assertTrue(AppLanguage.fromCode("xx").isEmpty() && AppLanguage.fromCode(null).isEmpty());
    }

    @Test
    void af4_choosingTheLanguageThatIsShownChangesAndStoresNothing() {
        openHome(Locale.ENGLISH);
        Mockito.clearInvocations(settings);

        find(LanguageSelector.class).single().setValue(AppLanguage.GERMAN);
        find(LanguageSelector.class).single().setValue(AppLanguage.GERMAN); // again: already German
        Mockito.clearInvocations(settings);
        find(LanguageSelector.class).single().setValue(AppLanguage.GERMAN);

        Mockito.verify(settings, Mockito.never()).saveAndFlush(any());
        assertFalse(service.saveLanguage(alice, AppLanguage.GERMAN), "Storing the stored language writes nothing");
    }

    // --- Business rules -------------------------------------------------------------------------

    @Test
    void br01_exactlyFourLanguagesAndEnglishIsTheFallback() {
        assertEquals(4, AppLanguage.values().length);
        assertEquals(AppLanguage.ENGLISH, AppLanguage.DEFAULT);
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromCode(" FR ").orElseThrow(), "Not case-sensitive");
    }

    @Test
    void br04_theSettingsLiveInTheirOwnTableWithOneRowPerEmployeeAndOnlyTheFourCodes() {
        long other = employees.findByEmailIgnoreCase(ALICE).orElseThrow().getId();
        jdbc.update("insert into employee_setting (employee_id, language, created_at, updated_at) values (?, 'es', current_timestamp, current_timestamp)", other);

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into employee_setting (employee_id, language, created_at, updated_at) values (?, 'de', current_timestamp, current_timestamp)", other),
                "One row per employee");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update employee_setting set language = 'it' where employee_id = ?", other),
                "The database only accepts the four codes");
        jdbc.update("update employee_setting set language = null where employee_id = ?", other); // "not chosen" is allowed
        assertTrue(service.languageOf(other).isEmpty());
        assertEquals(List.of(), List.of(Employee.class.getDeclaredFields()).stream().filter(f -> f.getName().equals("language")).toList(),
                "The language is not a column of the employee");
    }

    @Test
    void br05_theEmployeeFormHasNoLanguage() {
        assertTrue(List.of(EmployeeInput.class.getRecordComponents()).stream().noneMatch(c -> c.getName().toLowerCase().contains("language")));
    }

    @Test
    void br06_emailsAreSentInTheLanguageOfTheRecipientOrTheConfiguredDefault() {
        service.saveLanguage(alice, AppLanguage.SPANISH);
        Employee bob = employees.findByEmailIgnoreCase("bob.manager@example.com").orElseGet(() -> employees.save(
                new Employee("bob.manager@example.com", "Bob", "Manager", Role.MANAGER, employees.findAll().getFirst().getDepartmentId())));
        Instant at = Instant.parse("2026-10-02T10:00:00Z");

        emails.timesheetSubmitted(new TimesheetSubmittedNotice(ALICE, "Alice", "Erik", YearMonth.of(2026, 9), at, false));
        emails.timesheetSubmitted(new TimesheetSubmittedNotice(bob.getEmail(), "Bob", "Erik", YearMonth.of(2026, 9), at, false));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        Mockito.verify(mailSender, Mockito.times(2)).send(sent.capture());
        SimpleMailMessage toAlice = sent.getAllValues().stream().filter(m -> ALICE.equals(m.getTo()[0])).findFirst().orElseThrow();
        SimpleMailMessage toBob = sent.getAllValues().stream().filter(m -> !ALICE.equals(m.getTo()[0])).findFirst().orElseThrow();
        assertTrue(toAlice.getSubject().startsWith("Hoja de horas pendiente de aprobación"), toAlice.getSubject());
        assertTrue(toAlice.getSubject().contains("septiembre"), "Month names follow the language: " + toAlice.getSubject());
        assertTrue(toBob.getSubject().startsWith("Timesheet awaiting approval"), toBob.getSubject());
    }

    @Test
    void br11_changingTheLanguageIsNotAudited() {
        openHome(Locale.ENGLISH);

        find(LanguageSelector.class).single().setValue(AppLanguage.FRENCH);

        assertEquals(auditBefore, auditLog.findAllByOrderByIdAsc().size());
    }

    // --- helpers --------------------------------------------------------------------------------

    private void openHome(Locale locale) {
        UI.getCurrent().setLocale(locale);
        navigate(LoginView.class); // every call builds a fresh page
        navigate(HomeView.class);
    }

    private Authentication authentication() {
        Employee employee = employees.findById(alice).orElseThrow();
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "alice"));
        EmployeePrincipal principal = new EmployeePrincipal(employee, token);
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(authentication());
    }

    /** A request with the language remembered in the browser and the languages the browser asks for. */
    private static VaadinRequest request(String chosen, Locale... browser) {
        VaadinRequest request = Mockito.mock(VaadinRequest.class);
        Mockito.when(request.getCookies()).thenReturn(new Cookie[] { new Cookie(LanguageSelector.COOKIE, chosen) });
        Mockito.when(request.getLocales()).thenReturn(Collections.enumeration(List.of(browser)));
        return request;
    }
}
