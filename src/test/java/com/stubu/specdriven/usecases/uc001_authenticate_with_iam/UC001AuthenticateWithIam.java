package com.stubu.specdriven.usecases.uc001_authenticate_with_iam;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditLogEntry;
import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.home.HomeView;
import com.stubu.specdriven.security.IamEmailExtractor;
import com.stubu.specdriven.security.LoginView;
import com.stubu.specdriven.testsupport.TestBrowser;
import com.stubu.specdriven.testsupport.TestOidcProvider;
import com.stubu.specdriven.testsupport.TestSupportConfiguration;
import com.stubu.specdriven.testsupport.WithEmployee;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.QueryParameters;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * UC-001 Authenticate with IAM. Behaviour is verified end to end against a scripted OIDC identity
 * provider (real redirects, code exchange and signed ID tokens); the views are checked browserless.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSupportConfiguration.class)
class UC001AuthenticateWithIam extends SpringBrowserlessTest {

    private static final String PROVIDER = "mock";
    private static final String ALICE = "alice.employee@example.com";

    private static final String NOT_REGISTERED_MESSAGE = "Your account is not registered in the time tracking "
            + "system. Please contact your administrator.";
    private static final String INACTIVE_MESSAGE = "Your account has been deactivated. Please contact your "
            + "administrator.";
    private static final String UNAVAILABLE_MESSAGE = "Authentication service is unavailable. Please try again "
            + "later.";
    private static final String FAILED_MESSAGE = "Authentication failed. Please try again.";

    private static final TestOidcProvider IDP = TestOidcProvider.start();

    @DynamicPropertySource
    static void identityProvider(DynamicPropertyRegistry registry) {
        IDP.applicationProperties(PROVIDER).forEach((name, value) -> registry.add(name, () -> value));
    }

    @AfterAll
    static void stopIdentityProvider() {
        IDP.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    ClientRegistrationRepository clientRegistrations;
    @Autowired
    IamEmailExtractor emailExtractor;

    private TestBrowser browser;
    private int auditEntriesBefore;

    @BeforeEach
    void setUpScenario() {
        IDP.reset();
        browser = new TestBrowser(port);
        auditEntriesBefore = auditLog.findAllByOrderByIdAsc().size();
    }

    // --- Main Flow ------------------------------------------------------------------------------

    @Test
    void mainFlow_registeredEmployeeSignsInAndArrivesOnHomeDashboard() {
        Employee alice = employee(ALICE, Role.EMPLOYEE, true);
        IDP.signInAs(ALICE);

        // 1. The user arrives at the application and is sent to the login page.
        HttpResponse<String> entry = browser.get("/");
        assertEquals(302, entry.statusCode());
        assertEquals("/login", browser.location(entry).getPath());

        // 3.-4. Clicking a login option redirects to the IAM provider (OIDC authorization code flow).
        HttpResponse<String> toProvider = browser.startLogin(PROVIDER);
        assertEquals(302, toProvider.statusCode());
        String authorizationUrl = browser.location(toProvider).toString();
        assertTrue(authorizationUrl.startsWith(IDP.issuer() + "/authorize?"), authorizationUrl);
        assertTrue(authorizationUrl.contains("response_type=code"), authorizationUrl);
        assertTrue(authorizationUrl.contains("client_id=" + TestOidcProvider.CLIENT_ID), authorizationUrl);
        assertTrue(authorizationUrl.contains("scope=openid"), authorizationUrl);

        // 5.-6. The provider authenticates the user and redirects back with an authorization code.
        HttpResponse<String> fromProvider = browser.get(authorizationUrl);
        assertEquals(302, fromProvider.statusCode());
        String callbackUrl = browser.location(fromProvider).toString();
        assertTrue(callbackUrl.startsWith(browser.baseUri() + "/login/oauth2/code/" + PROVIDER + "?"), callbackUrl);
        assertTrue(callbackUrl.contains("code="), callbackUrl);

        // 7.-11. The code is exchanged for tokens, the employee is resolved and the user lands on the dashboard.
        HttpResponse<String> callback = browser.get(browser.location(fromProvider).toString());
        assertEquals(302, callback.statusCode());
        assertEquals("/", browser.location(callback).getPath());

        // Postconditions: an active session with the employee's identity and roles, and an audit entry.
        HttpResponse<String> whoAmI = browser.get("/test/whoami");
        assertEquals(200, whoAmI.statusCode());
        assertEquals("employeeId=" + alice.getId() + ";email=" + ALICE + ";roles=ROLE_EMPLOYEE", whoAmI.body());
        assertEquals(200, browser.get("/").statusCode());
        AuditLogEntry entry1 = onlyNewAuditEntry();
        assertEquals(AuditAction.LOGIN_SUCCESS, entry1.getAction());
        assertEquals(alice.getId(), entry1.getUserId());
        assertRecent(entry1);
    }

    @Test
    void mainFlow_managerAndAdministratorAlsoGetTheEmployeeRole() {
        Employee bob = employee("bob.manager@example.com", Role.MANAGER, true);
        Employee carol = employee("carol.admin@example.com", Role.ADMIN, true);

        IDP.signInAs(bob.getEmail());
        assertEquals("/", browser.location(browser.signIn(PROVIDER)).getPath());
        assertEquals("employeeId=" + bob.getId() + ";email=" + bob.getEmail() + ";roles=ROLE_EMPLOYEE,ROLE_MANAGER",
                browser.get("/test/whoami").body());

        TestBrowser adminBrowser = new TestBrowser(port);
        IDP.signInAs(carol.getEmail());
        assertEquals("/", adminBrowser.location(adminBrowser.signIn(PROVIDER)).getPath());
        assertEquals("employeeId=" + carol.getId() + ";email=" + carol.getEmail() + ";roles=ROLE_ADMIN,ROLE_EMPLOYEE",
                adminBrowser.get("/test/whoami").body());
    }

    @Test
    @WithAnonymousUser
    void mainFlow_loginPageOffersEveryConfiguredProvider() {
        UI.getCurrent().setLocale(Locale.ENGLISH);

        LoginView login = navigate(LoginView.class);

        List<Anchor> options = find(Anchor.class).from(login).all();
        assertEquals(List.of("Sign in with Google", "Sign in with Microsoft", "Sign in with Test IdP"),
                options.stream().map(Anchor::getText).toList());
        assertEquals(List.of("/oauth2/authorization/google", "/oauth2/authorization/microsoft",
                "/oauth2/authorization/" + PROVIDER), options.stream().map(Anchor::getHref).toList());
        assertTrue(options.stream().allMatch(Anchor::isRouterIgnore),
                "Login options must be full page navigations to Spring Security, not client-side routes");
        assertFalse(login.getElement().getTextRecursively().contains("Authentication failed"));
    }

    @Test
    @WithAnonymousUser
    void mainFlow_loginPageIsGermanForGermanUsers() {
        UI.getCurrent().setLocale(Locale.GERMAN);

        LoginView login = navigate(LoginView.class);

        assertEquals(List.of("Mit Google anmelden", "Mit Microsoft anmelden", "Mit Test IdP anmelden"),
                find(Anchor.class).from(login).all().stream().map(Anchor::getText).toList());
    }

    @Test
    @WithAnonymousUser
    void mainFlow_loginPageIsEnglishForEnglishUsersEvenOnAGermanServer() {
        Locale serverLocale = Locale.getDefault();
        Locale.setDefault(Locale.GERMAN);
        try {
            UI.getCurrent().setLocale(Locale.ENGLISH);

            LoginView login = navigate(LoginView.class);

            assertEquals("Sign in with Google", find(Anchor.class).from(login).all().getFirst().getText());
        } finally {
            Locale.setDefault(serverLocale);
        }
    }

    @Test
    @WithAnonymousUser
    void mainFlow_microsoftAndGoogleRedirectToTheirAuthorizationEndpoints() {
        String google = browser.location(browser.startLogin("google")).toString();
        assertTrue(google.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"), google);
        assertTrue(google.contains("client_id=test-google-client"), google);

        String microsoft = browser.location(browser.startLogin("microsoft")).toString();
        assertTrue(microsoft.startsWith("https://login.microsoftonline.com/test-tenant/oauth2/v2.0/authorize?"),
                microsoft);
        assertTrue(microsoft.contains("client_id=test-microsoft-client"), microsoft);
    }

    @Test
    @WithAnonymousUser
    void mainFlow_anonymousUserIsSentToLoginPage() {
        navigate("", LoginView.class);
    }

    @Test
    @WithEmployee(firstName = "Alice", lastName = "Employee", role = Role.EMPLOYEE)
    void mainFlow_homeDashboardForEmployee() {
        UI.getCurrent().setLocale(Locale.ENGLISH);

        HomeView home = navigate(HomeView.class);

        assertEquals("Welcome, Alice Employee", find(H2.class).from(home).single().getText());
        assertEquals("Employee", find(Badge.class).from(home).single().getText());
        assertTrue(home.getElement().getTextRecursively().contains("record and review your working time"));
    }

    @Test
    @WithEmployee(firstName = "Bob", lastName = "Manager", email = "bob.manager@example.com", role = Role.MANAGER)
    void mainFlow_homeDashboardForManager() {
        UI.getCurrent().setLocale(Locale.ENGLISH);

        HomeView home = navigate(HomeView.class);

        assertEquals("Welcome, Bob Manager", find(H2.class).from(home).single().getText());
        assertEquals("Manager", find(Badge.class).from(home).single().getText());
        assertTrue(home.getElement().getTextRecursively().contains("timesheets of your team"));
    }

    @Test
    @WithEmployee(firstName = "Carol", lastName = "Admin", email = "carol.admin@example.com", role = Role.ADMIN)
    void mainFlow_homeDashboardForAdministrator() {
        UI.getCurrent().setLocale(Locale.ENGLISH);

        HomeView home = navigate(HomeView.class);

        assertEquals("Welcome, Carol Admin", find(H2.class).from(home).single().getText());
        assertEquals("Administrator", find(Badge.class).from(home).single().getText());
        assertTrue(home.getElement().getTextRecursively().contains("manage employees"));
    }

    // --- AF-1: Employee Not Found ---------------------------------------------------------------

    @Test
    void af1_employeeNotFound_isDeniedAndNotCreated() {
        long employeeCount = employees.count();
        IDP.signInAs("stranger@example.com");

        HttpResponse<String> callback = browser.signIn(PROVIDER);

        assertLoginRejected(callback, "not-registered");
        assertEquals(employeeCount, employees.count(), "An employee must never be created from an IAM login");
    }

    @Test
    @WithAnonymousUser
    void af1_employeeNotFound_showsMessage() {
        assertLoginMessage("not-registered", NOT_REGISTERED_MESSAGE);
    }

    // --- AF-2: Employee Account Inactive --------------------------------------------------------

    @Test
    void af2_employeeInactive_isDenied() {
        Employee dave = employee("dave.inactive@example.com", Role.EMPLOYEE, false);
        IDP.signInAs(dave.getEmail());

        HttpResponse<String> callback = browser.signIn(PROVIDER);

        assertLoginRejected(callback, "inactive");
        assertEquals(dave.getId(), onlyNewAuditEntry().getUserId());
    }

    @Test
    @WithAnonymousUser
    void af2_employeeInactive_showsMessage() {
        assertLoginMessage("inactive", INACTIVE_MESSAGE);
    }

    // --- AF-3: IAM Provider Error ---------------------------------------------------------------

    @Test
    void af3_providerReturnsError_showsUnavailable() {
        employee(ALICE, Role.EMPLOYEE, true);
        IDP.failNextAuthorization("server_error");

        HttpResponse<String> callback = browser.signIn(PROVIDER);

        assertLoginRejected(callback, "unavailable");
    }

    @Test
    void af3_providerTokenEndpointFails_showsUnavailable() {
        employee(ALICE, Role.EMPLOYEE, true);
        IDP.signInAs(ALICE);
        IDP.failTokenEndpoint(503);

        HttpResponse<String> callback = browser.signIn(PROVIDER);

        assertLoginRejected(callback, "unavailable");
    }

    @Test
    void af3_providerUnreachable_showsUnavailable() {
        employee(ALICE, Role.EMPLOYEE, true);
        IDP.signInAs(ALICE);
        IDP.failTokenEndpoint(TestOidcProvider.DROP_CONNECTION);

        HttpResponse<String> callback = browser.signIn(PROVIDER);

        assertLoginRejected(callback, "unavailable");
    }

    @Test
    @WithAnonymousUser
    void af3_providerError_showsMessage() {
        assertLoginMessage("unavailable", UNAVAILABLE_MESSAGE);
    }

    // --- AF-4: Token Exchange Failure -----------------------------------------------------------

    @Test
    void af4_invalidAuthorizationCode_showsAuthenticationFailed() {
        employee(ALICE, Role.EMPLOYEE, true);
        IDP.signInAs(ALICE);
        String authorizationUrl = browser.location(browser.startLogin(PROVIDER)).toString();
        String state = queryParameter(authorizationUrl, "state");

        HttpResponse<String> callback = browser.get("/login/oauth2/code/" + PROVIDER + "?code=forged&state=" + state);

        assertLoginRejected(callback, "failed");
    }

    @Test
    void af4_callbackWithoutLoginAttempt_showsAuthenticationFailed() {
        HttpResponse<String> callback = browser.get("/login/oauth2/code/" + PROVIDER + "?code=x&state=unknown");

        assertLoginRejected(callback, "failed");
    }

    @Test
    void af4_providerDoesNotVouchForTheEmail_showsAuthenticationFailed() {
        employee(ALICE, Role.EMPLOYEE, true);
        IDP.signInAs(ALICE, false);

        HttpResponse<String> callback = browser.signIn(PROVIDER);

        assertLoginRejected(callback, "failed");
    }

    @Test
    @WithAnonymousUser
    void af4_authenticationFailed_showsMessage() {
        assertLoginMessage("failed", FAILED_MESSAGE);
    }

    @Test
    @WithAnonymousUser
    void af4_unknownErrorCode_showsAuthenticationFailed() {
        assertLoginMessage("something-unexpected", FAILED_MESSAGE);
    }

    // --- Business Rules -------------------------------------------------------------------------

    @Test
    void br01_onlyOidcProvidersAreSupported() {
        List<ClientRegistration> registrations = registeredProviders();
        assertEquals(3, registrations.size());
        for (ClientRegistration registration : registrations) {
            assertEquals(AuthorizationGrantType.AUTHORIZATION_CODE, registration.getAuthorizationGrantType());
            assertTrue(registration.getScopes().contains("openid"), registration.getRegistrationId());
        }

        // There is no username/password login: posting credentials does not authenticate anybody.
        employee(ALICE, Role.EMPLOYEE, true);
        browser.postForm("/login", Map.of("username", ALICE, "password", "secret"));
        assertEquals(302, browser.get("/test/whoami").statusCode());
        assertEquals("/login", browser.location(browser.get("/test/whoami")).getPath());

        // Nor can an arbitrary provider be started.
        String unknown = browser.location(browser.get("/oauth2/authorization/github")).toString();
        assertFalse(unknown.contains("github"), unknown);
    }

    @Test
    void br02_emailExtractionDoesNotDependOnTheProvider() {
        // Google style: standard email claim.
        assertEquals(Optional.of("alice@example.com"), emailExtractor.extractEmail(
                Map.of("sub", "1", "email", "alice@example.com", "email_verified", true), "google"));
        // Microsoft Entra ID style: only the user principal name.
        assertEquals(Optional.of("alice@example.com"), emailExtractor.extractEmail(
                Map.of("sub", "2", "preferred_username", "alice@example.com"), "microsoft"));
        // An address the provider flags as unverified is not trusted.
        assertEquals(Optional.empty(), emailExtractor.extractEmail(
                Map.of("email", "alice@example.com", "email_verified", false), "google"));
        // No usable address at all.
        assertEquals(Optional.empty(), emailExtractor.extractEmail(Map.of("sub", "3", "name", "Alice"), "google"));
    }

    @Test
    void br02_employeeIsFoundRegardlessOfEmailCase() {
        Employee alice = employee(ALICE, Role.EMPLOYEE, true);
        IDP.signInAs("Alice.Employee@Example.COM");

        HttpResponse<String> callback = browser.signIn(PROVIDER);

        assertEquals("/", browser.location(callback).getPath());
        assertEquals("employeeId=" + alice.getId() + ";email=" + ALICE + ";roles=ROLE_EMPLOYEE",
                browser.get("/test/whoami").body());
    }

    @Test
    void br03_onlyActiveEmployeesAreGrantedAccess() {
        Employee erin = employee("erin.employee@example.com", Role.EMPLOYEE, false);
        IDP.signInAs(erin.getEmail());
        assertLoginRejected(browser.signIn(PROVIDER), "inactive");

        erin.setActive(true);
        employees.save(erin);

        TestBrowser freshBrowser = new TestBrowser(port);
        IDP.signInAs(erin.getEmail());
        assertEquals("/", freshBrowser.location(freshBrowser.signIn(PROVIDER)).getPath());
        assertEquals(200, freshBrowser.get("/test/whoami").statusCode());
    }

    @Test
    void br04_sessionCookieIsHttpOnlyAndSameSiteLax() {
        employee(ALICE, Role.EMPLOYEE, true);
        IDP.signInAs(ALICE);

        HttpResponse<String> callback = browser.signIn(PROVIDER);

        String sessionCookie = TestBrowser.setCookies(callback).stream()
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No session cookie was issued"));
        assertTrue(sessionCookie.toLowerCase(Locale.ROOT).contains("httponly"), sessionCookie);
        assertTrue(sessionCookie.toLowerCase(Locale.ROOT).contains("samesite=lax"), sessionCookie);
    }

    @Test
    void br04_sessionIsValidatedOnEveryRequest() {
        employee(ALICE, Role.EMPLOYEE, true);

        // Without a session, protected content is refused.
        HttpResponse<String> anonymous = browser.get("/test/whoami");
        assertEquals(302, anonymous.statusCode());
        assertEquals("/login", browser.location(anonymous).getPath());

        // A forged or stale session id is worth nothing.
        HttpResponse<String> forged = browser.getWithCookie("/test/whoami", "JSESSIONID=0123456789ABCDEF");
        assertEquals(302, forged.statusCode());
        assertEquals("/login", browser.location(forged).getPath());

        // The real session is accepted on repeated requests.
        IDP.signInAs(ALICE);
        browser.signIn(PROVIDER);
        assertEquals(200, browser.get("/test/whoami").statusCode());
        assertEquals(200, browser.get("/test/whoami").statusCode());
    }

    @Test
    void br05_everyFailedAttemptIsAudited() {
        employee(ALICE, Role.EMPLOYEE, true);
        employee("dave.inactive@example.com", Role.EMPLOYEE, false);

        IDP.signInAs("stranger@example.com");
        browser.signIn(PROVIDER);
        IDP.signInAs("dave.inactive@example.com");
        browser.signIn(PROVIDER);
        IDP.signInAs(ALICE);
        IDP.failTokenEndpoint(503);
        browser.signIn(PROVIDER);
        IDP.reset();
        browser.get("/login/oauth2/code/" + PROVIDER + "?code=x&state=unknown");

        List<AuditLogEntry> entries = newAuditEntries();
        assertEquals(4, entries.size());
        assertTrue(entries.stream().allMatch(entry -> entry.getAction() == AuditAction.LOGIN_FAILURE));
        assertEquals(List.of(true, true, false, false), entries.stream()
                .map(entry -> entry.getReason().contains("@")).toList(),
                "The attempted email is recorded whenever it is known");
        assertTrue(entries.get(0).getReason().startsWith("NOT_REGISTERED"), entries.get(0).getReason());
        assertTrue(entries.get(1).getReason().startsWith("INACTIVE"), entries.get(1).getReason());
        assertTrue(entries.get(2).getReason().startsWith("UNAVAILABLE"), entries.get(2).getReason());
        assertTrue(entries.get(3).getReason().startsWith("FAILED"), entries.get(3).getReason());
        entries.forEach(UC001AuthenticateWithIam::assertRecent);
    }

    @Test
    void br05_auditRecordsCannotBeChangedOrRemovedThroughTheApplication() {
        List<String> operations = Arrays.stream(AuditLogRepository.class.getMethods()).map(Method::getName).toList();
        assertTrue(operations.stream().noneMatch(name -> name.startsWith("delete") || name.startsWith("remove")),
                operations.toString());
        Constructor<AuditLogEntry> constructor = assertDoesNotThrow(
                () -> AuditLogEntry.class.getDeclaredConstructor(Instant.class, Long.class, String.class, Long.class,
                        AuditAction.class, String.class, String.class, String.class));
        assertFalse(Modifier.isPublic(constructor.getModifiers()),
                "Audit entries can only be created by the audit service");
        assertTrue(Arrays.stream(AuditLogEntry.class.getMethods()).noneMatch(method -> method.getName().startsWith("set")),
                "Audit entries have no setters");
    }

    // --- helpers --------------------------------------------------------------------------------

    private Employee employee(String email, Role role, boolean active) {
        Employee employee = employees.findByEmailIgnoreCase(email).orElseGet(() -> {
            Department department = departments.findAll().stream().findFirst()
                    .orElseGet(() -> departments.save(new Department("Engineering")));
            return new Employee(email, firstNameOf(email), lastNameOf(role), role, department.getId());
        });
        employee.setActive(active);
        return employees.save(employee);
    }

    /** Matches the names @WithEmployee uses (Alice Employee, Bob Manager, Carol Admin), which share these rows. */
    private static String lastNameOf(Role role) {
        return role.name().charAt(0) + role.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private static String firstNameOf(String email) {
        String local = email.substring(0, email.indexOf('@'));
        String first = local.contains(".") ? local.substring(0, local.indexOf('.')) : local;
        return Character.toUpperCase(first.charAt(0)) + first.substring(1);
    }

    private List<ClientRegistration> registeredProviders() {
        List<ClientRegistration> registrations = new ArrayList<>();
        ((Iterable<?>) clientRegistrations).forEach(r -> registrations.add((ClientRegistration) r));
        return registrations;
    }

    /** The login was rejected: back on the login page with the given error, no session, and an audit entry. */
    private void assertLoginRejected(HttpResponse<String> callback, String errorCode) {
        assertEquals(302, callback.statusCode());
        assertEquals("/login?error=" + errorCode, relative(browser.location(callback).toString()));
        HttpResponse<String> whoAmI = browser.get("/test/whoami");
        assertEquals(302, whoAmI.statusCode(), "No session may be established after a failed login");
        assertEquals("/login", browser.location(whoAmI).getPath());
        AuditLogEntry entry = onlyNewAuditEntry();
        assertEquals(AuditAction.LOGIN_FAILURE, entry.getAction());
        assertRecent(entry);
    }

    private void assertLoginMessage(String errorCode, String expectedMessage) {
        UI.getCurrent().setLocale(Locale.ENGLISH);
        UI.getCurrent().navigate("login", QueryParameters.of("error", errorCode));

        LoginView login = assertInstanceOf(LoginView.class, getCurrentView());
        assertTrue(login.getElement().getTextRecursively().contains(expectedMessage),
                login.getElement().getTextRecursively());
        assertNotNull(find(Anchor.class).from(login).all(), "The provider buttons stay available to try again");
    }

    private AuditLogEntry onlyNewAuditEntry() {
        List<AuditLogEntry> entries = newAuditEntries();
        assertEquals(1, entries.size(), "Expected exactly one new audit entry but found " + entries.size());
        return entries.get(0);
    }

    private List<AuditLogEntry> newAuditEntries() {
        List<AuditLogEntry> all = auditLog.findAllByOrderByIdAsc();
        return all.subList(auditEntriesBefore, all.size());
    }

    private static void assertRecent(AuditLogEntry entry) {
        assertNotNull(entry.getTimestamp());
        assertTrue(Duration.between(entry.getTimestamp(), Instant.now()).abs().getSeconds() < 30,
                "Audit timestamp should come from the server clock: " + entry.getTimestamp());
    }

    private static String relative(String url) {
        int path = url.indexOf('/', url.indexOf("//") + 2);
        return url.startsWith("http") ? url.substring(path) : url;
    }

    private static String queryParameter(String url, String name) {
        for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
            if (pair.startsWith(name + "=")) {
                return pair.substring(name.length() + 1);
            }
        }
        throw new AssertionError("No parameter " + name + " in " + url);
    }

}
