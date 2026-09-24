package com.stubu.specdriven.security;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Login page: one "Sign in with ..." button per configured OIDC provider. Clicking one starts the
 * OIDC flow at Spring Security's authorization endpoint; there is no username/password form.
 * After a failed login the user is sent back here with {@code ?error=<code>}.
 */
@Route(LoginView.ROUTE)
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle, LocaleChangeObserver {

    public static final String ROUTE = "login";
    static final String AUTHORIZATION_PATH = "/oauth2/authorization/";

    private final List<ClientRegistration> providers = new ArrayList<>();
    private final H1 title = new H1();
    private final Paragraph intro = new Paragraph();
    private final Div message = new Div();
    private final VerticalLayout providerButtons = new VerticalLayout();
    private LoginError error;

    public LoginView(ClientRegistrationRepository registrations) {
        if (registrations instanceof Iterable<?> configured) {
            configured.forEach(registration -> providers.add((ClientRegistration) registration));
        }

        addClassName("login-view");
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        message.addClassName("login-message");
        message.getElement().setAttribute("role", "alert");
        providerButtons.addClassName("login-providers");
        providerButtons.setPadding(false);
        providerButtons.setSpacing(true);

        VerticalLayout card = new VerticalLayout(title, intro, message, providerButtons);
        card.addClassNames("login-card", "aura-surface-solid");
        card.setPadding(true);
        card.setSpacing(true);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> errorCodes = event.getLocation().getQueryParameters().getParameters().get("error");
        error = errorCodes == null ? null
                : errorCodes.stream().findFirst().flatMap(LoginError::fromCode).orElse(LoginError.FAILED);
        if (isAttached()) {
            // Navigating to the login page again (only the query changed) reuses this instance.
            render();
        }
    }

    /** Called when the view is attached and whenever the locale changes. */
    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    /** (Re)builds everything that depends on the locale or the error to show. */
    private void render() {
        title.setText(getTranslation("app.title"));
        intro.setText(getTranslation("login.intro"));

        providerButtons.removeAll();
        providers.forEach(provider -> providerButtons.add(providerButton(provider)));

        if (error != null) {
            showMessage(getTranslation(error.messageKey()));
        } else if (providers.isEmpty()) {
            showMessage(getTranslation("login.noProviders"));
        } else {
            message.setVisible(false);
        }
    }

    private Anchor providerButton(ClientRegistration registration) {
        Anchor button = new Anchor(AUTHORIZATION_PATH + registration.getRegistrationId(),
                getTranslation("login.signInWith", registration.getClientName()));
        // Full page navigation to Spring Security, not a client-side route.
        button.setRouterIgnore(true);
        button.addClassName("login-provider-button");
        button.setTestId("login-" + registration.getRegistrationId());
        return button;
    }

    private void showMessage(String text) {
        message.removeAll();
        var icon = VaadinIcon.WARNING.create();
        icon.getElement().setAttribute("aria-hidden", "true");
        message.add(icon, new Span(text));
        message.setVisible(true);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("login.title");
    }
}
