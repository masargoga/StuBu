package com.stubu.specdriven.testsupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.Role;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for real-browser tests: the whole application runs on a random port with the test identity
 * provider (interactive sign-in form) and a controllable server clock, and Playwright drives Chrome against
 * it. These tests are tagged {@code e2e} and only run with {@code .\mvnw test -Pe2e}.
 *
 * <p>The browser is the Chrome installed on the machine (system property {@code e2e.browserChannel},
 * default {@code chrome}; {@code msedge} works too), so nothing is downloaded. It runs headless unless
 * {@code -De2e.headed=true}. The browser reports UTC and the en-US locale, which keeps the times on screen
 * predictable. Screenshots go to {@code target/e2e-screenshots}.
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "vaadin.launch-browser=false", "vaadin.devmode.devTools.enabled=false" })
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
public abstract class E2ETest {

    /** The three screen sizes the application must work at (see the design system). */
    public record Viewport(String name, int width, int height) {
        @Override
        public String toString() {
            return name + " " + width + "x" + height;
        }
    }

    public static final Viewport DESKTOP = new Viewport("desktop", 1920, 1080);
    public static final Viewport TABLET = new Viewport("tablet", 768, 1024);
    public static final Viewport MOBILE = new Viewport("mobile", 375, 812);

    protected static final String ALICE = "alice.employee@example.com";

    protected static final TestOidcProvider IDP = TestOidcProvider.start(0, true);

    private static Playwright playwright;
    private static Browser browser;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            IDP.close();
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        }));
    }

    @DynamicPropertySource
    static void identityProvider(DynamicPropertyRegistry registry) {
        IDP.applicationProperties("mock").forEach((name, value) -> registry.add(name, () -> value));
    }

    @LocalServerPort
    protected int port;
    @Autowired
    protected MutableClock clock;
    @Autowired
    private EmployeeRepository employees;
    @Autowired
    private DepartmentRepository departments;
    @Autowired
    private JdbcTemplate jdbc;

    protected BrowserContext context;
    protected Page page;

    @BeforeEach
    void resetApplicationState() {
        clock.set(TestClockConfiguration.START);
        jdbc.update("delete from time_entry");
        employee(ALICE, "Alice", "Employee", Role.EMPLOYEE, true);
    }

    @AfterEach
    void closeBrowserContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    // --- browser -------------------------------------------------------------------------------

    /** Opens a fresh browser window (own cookies, so nobody is signed in) at the given size. */
    protected Page open(Viewport viewport) {
        return open(viewport, "en-US");
    }

    /** Like {@link #open(Viewport)} for a browser with the given language, e.g. {@code de-DE}. */
    protected Page open(Viewport viewport, String locale) {
        if (context != null) {
            context.close();
        }
        context = browser().newContext(new Browser.NewContextOptions()
                .setViewportSize(viewport.width(), viewport.height())
                .setLocale(locale)
                .setTimezoneId("UTC"));
        context.setDefaultTimeout(15_000);
        page = context.newPage();
        return page;
    }

    private static synchronized Browser browser() {
        if (browser == null) {
            String channel = System.getProperty("e2e.browserChannel", "chrome");
            playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(!Boolean.getBoolean("e2e.headed"));
            if (!channel.isBlank()) {
                options.setChannel(channel);
            }
            browser = playwright.chromium().launch(options);
        }
        return browser;
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Signs in through the test identity provider, like a user clicking the login button and typing an email. */
    protected void signIn(String email) {
        page.navigate(url("/login"));
        page.getByText("Sign in with Test IdP").click();
        page.locator("#email").fill(email);
        page.locator("#email").press("Enter");
        page.waitForURL(url("/**"));
    }

    /** Signs in as Alice and waits for the time tracking panel. */
    protected void signInAsAlice() {
        signIn(ALICE);
        page.locator("[data-testid=check-in]").waitFor();
    }

    protected void screenshot(String name) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Path.of("target", "e2e-screenshots", getClass().getSimpleName(), name + ".png")));
    }

    // --- application data ------------------------------------------------------------------------

    protected Employee employee(String email, String firstName, String lastName, Role role, boolean active) {
        Employee employee = employees.findByEmailIgnoreCase(email).orElseGet(() -> {
            Department department = departments.findAll().stream().findFirst()
                    .orElseGet(() -> departments.save(new Department("Engineering")));
            return new Employee(email, firstName, lastName, role, department.getId());
        });
        employee.setActive(active);
        return employees.save(employee);
    }

    // --- measurements ----------------------------------------------------------------------------

    /** Visible text of an element with non-breaking spaces (used by the time format) turned into normal ones. */
    protected static String text(Locator locator) {
        return locator.innerText().replace(' ', ' ').replace(' ', ' ').strip();
    }

    protected double[] box(String selector) {
        Object result = page.evaluate("""
                selector => {
                  const r = document.querySelector(selector).getBoundingClientRect();
                  return [r.x, r.y, r.width, r.height];
                }""", selector);
        List<?> values = (List<?>) result;
        return values.stream().mapToDouble(v -> ((Number) v).doubleValue()).toArray();
    }

    /**
     * The navigation drawer is permanently open beside the content on wide screens (and the content must start to
     * its right); on narrower screens it is closed and opened with the toggle in the header.
     */
    protected void assertNavigationLayout(Viewport viewport, String contentSelector) {
        assertTrue(page.getByTestId("drawer-toggle").isVisible(), "The navigation toggle is in the header");
        Locator today = page.getByTestId("nav-today");
        if (viewport.width() >= 1024) {
            assertTrue(today.isVisible(), "The drawer is open on wide screens");
            var navigation = today.boundingBox();
            double content = box(contentSelector)[0];
            assertTrue(content >= navigation.x + navigation.width, "Content (" + content + ") must start to the right of "
                    + "the navigation (" + (navigation.x + navigation.width) + ")");
        } else {
            assertTrue(!today.isVisible(), "The drawer is closed on narrow screens");
            assertTrue(box(contentSelector)[0] >= 0, "Content starts inside the screen");
        }
    }

    /** No horizontal scrolling: the page is never wider than the window. */
    protected void assertNoHorizontalOverflow() {
        Object widths = page.evaluate("[document.documentElement.scrollWidth, window.innerWidth]");
        List<?> values = (List<?>) widths;
        int scrollWidth = ((Number) values.get(0)).intValue();
        int windowWidth = ((Number) values.get(1)).intValue();
        assertTrue(scrollWidth <= windowWidth, "Page is " + scrollWidth + "px wide in a " + windowWidth + "px window");
    }

    /**
     * WCAG contrast ratio between the text colour and the effective background of the first element matching
     * the selector (semi-transparent backgrounds are composited over their ancestors).
     */
    protected double contrast(String selector) {
        Object ratio = page.evaluate("""
                selector => {
                  const canvas = document.createElement('canvas');
                  canvas.width = canvas.height = 1;
                  const ctx = canvas.getContext('2d', { willReadFrequently: true });
                  const rgba = css => {
                    ctx.clearRect(0, 0, 1, 1);
                    ctx.fillStyle = '#010203';
                    ctx.fillStyle = css;
                    ctx.fillRect(0, 0, 1, 1);
                    const d = ctx.getImageData(0, 0, 1, 1).data;
                    return [d[0], d[1], d[2], d[3] / 255];
                  };
                  const over = (top, bottom) => {
                    const a = top[3] + bottom[3] * (1 - top[3]);
                    if (a === 0) return [0, 0, 0, 0];
                    return [0, 1, 2].map(i => (top[i] * top[3] + bottom[i] * bottom[3] * (1 - top[3])) / a).concat([a]);
                  };
                  const el = document.querySelector(selector);
                  const layers = [];
                  for (let n = el; n; n = n.parentElement) {
                    const bg = rgba(getComputedStyle(n).backgroundColor);
                    if (bg[3] > 0) layers.push(bg);
                    if (bg[3] === 1) break;
                  }
                  let background = [255, 255, 255, 1];
                  for (let i = layers.length - 1; i >= 0; i--) background = over(layers[i], background);
                  const text = over(rgba(getComputedStyle(el).color), background);
                  const lum = c => {
                    const [r, g, b] = c.slice(0, 3).map(v => {
                      v /= 255;
                      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
                    });
                    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
                  };
                  const l1 = lum(text), l2 = lum(background);
                  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
                }""", selector);
        return ((Number) ratio).doubleValue();
    }

    protected void assertReadable(String selector) {
        double ratio = contrast(selector);
        assertTrue(ratio >= 4.5, selector + " has a contrast ratio of " + String.format("%.2f", ratio)
                + ", WCAG AA requires 4.5");
    }
}
