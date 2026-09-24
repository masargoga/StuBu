package com.stubu.specdriven.usecases.uc001_authenticate_with_iam;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.testsupport.E2ETest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/**
 * UC-001 Authenticate with IAM in a real browser: what the user sees and can operate. The behaviour behind
 * it (flows, business rules) is covered by {@code UC001AuthenticateWithIam}.
 */
class UC001AuthenticateWithIamE2E extends E2ETest {

    static Stream<Viewport> viewports() {
        return Stream.of(DESKTOP, TABLET, MOBILE);
    }

    static Stream<Arguments> loginErrors() {
        return Stream.of(
                Arguments.of("not-registered", "Your account is not registered in the time tracking system. "
                        + "Please contact your administrator."),
                Arguments.of("inactive", "Your account has been deactivated. Please contact your administrator."),
                Arguments.of("unavailable", "Authentication service is unavailable. Please try again later."),
                Arguments.of("failed", "Authentication failed. Please try again."));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void loginPage_isReadableAndUsableAtEveryScreenSize(Viewport viewport) {
        open(viewport);
        page.navigate(url("/login"));
        Locator buttons = page.locator(".login-provider-button");
        buttons.first().waitFor();

        assertEquals(3, buttons.count(), "One button per configured provider");
        for (int i = 0; i < buttons.count(); i++) {
            var box = buttons.nth(i).boundingBox();
            assertTrue(box.height >= 56, "Large sign-in buttons for older users, but this one is " + box.height);
            assertTrue(box.x >= 0 && box.x + box.width <= viewport.width(), "Button fits the screen");
        }
        assertNoHorizontalOverflow();
        double[] card = box(".login-card");
        assertTrue(card[0] >= 0 && card[0] + card[2] <= viewport.width(), "Card fits the screen");
        assertEquals(card[0], viewport.width() - (card[0] + card[2]), 2.0, "Card is centered");
        assertReadable(".login-card p");
        assertReadable(".login-provider-button");
        screenshot("login-" + viewport.name());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("loginErrors")
    void loginPage_showsEachFailureAsAReadableMessage(String code, String message) {
        open(MOBILE);
        page.navigate(url("/login?error=" + code));

        Locator alert = page.locator(".login-message");
        assertThat(alert).isVisible();
        assertThat(alert).hasAttribute("role", "alert");
        assertThat(alert).containsText(message);
        assertThat(page.locator(".login-provider-button").first()).isVisible(); // the user can try again
        assertReadable(".login-message");
        assertNoHorizontalOverflow();
        screenshot("login-error-" + code);
    }

    @Test
    void signingIn_showsTheHomePageAndSigningOutReturnsToTheLoginPage() {
        open(DESKTOP);

        signInAsAlice();

        assertThat(page.locator(".home-welcome")).hasText("Welcome, Alice Employee");
        assertThat(page.getByTestId("role-badge")).hasText("Employee");
        screenshot("home-signed-in");

        page.getByTestId("sign-out").click();
        page.waitForURL(Pattern.compile(".*/login.*"));
        assertThat(page.locator(".login-provider-button").first()).isVisible();
    }

    @Test
    void anEmployeeWhoIsNotRegisteredIsSentBackWithAnExplanation() {
        open(DESKTOP);

        signIn("stranger@example.com");

        assertThat(page.locator(".login-message")).containsText("not registered");
        screenshot("login-denied-not-registered");
    }

    @Test
    void aDeactivatedEmployeeIsSentBackWithAnExplanation() {
        employee("dave.inactive@example.com", "Dave", "Inactive", Role.EMPLOYEE, false);
        open(DESKTOP);

        signIn("dave.inactive@example.com");

        assertThat(page.locator(".login-message")).containsText("has been deactivated");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("viewports")
    void theHomePageLaysOutNavigationHeaderAndContentAtEveryScreenSize(Viewport viewport) {
        open(viewport);

        signInAsAlice();

        assertNavigationLayout(viewport, ".home-welcome");
        assertNoHorizontalOverflow();
        assertReadable(".home-description");
    }
}
