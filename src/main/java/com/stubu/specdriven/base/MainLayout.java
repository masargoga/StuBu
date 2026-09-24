package com.stubu.specdriven.base;

import com.stubu.specdriven.security.EmployeePrincipal;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

/** Application shell for authenticated users: title, who is signed in, and sign out. */
@PermitAll
public class MainLayout extends AppLayout implements LocaleChangeObserver {

    private final H1 title = new H1();
    private final Button signOut;

    public MainLayout(AuthenticationContext authenticationContext) {
        title.addClassName("app-title");

        HorizontalLayout user = new HorizontalLayout();
        user.addClassName("app-user");
        user.setAlignItems(FlexComponent.Alignment.CENTER);
        authenticationContext.getAuthenticatedUser(Object.class).ifPresent(principal -> {
            String name = principal instanceof EmployeePrincipal employee ? employee.getFullName()
                    : authenticationContext.getPrincipalName().orElse("");
            Span userName = new Span(name);
            userName.addClassName("app-user-name");
            user.add(userName);
        });
        signOut = new Button();
        signOut.addClickListener(event -> authenticationContext.logout());
        signOut.addThemeVariants(ButtonVariant.TERTIARY);
        signOut.setTestId("sign-out");
        user.add(signOut);

        HorizontalLayout navbar = new HorizontalLayout(title, user);
        navbar.addClassName("app-navbar");
        navbar.setWidthFull();
        navbar.setAlignItems(FlexComponent.Alignment.CENTER);
        navbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        navbar.expand(title);
        addToNavbar(navbar);
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        title.setText(getTranslation("app.title"));
        signOut.setText(getTranslation("app.signOut"));
    }
}
