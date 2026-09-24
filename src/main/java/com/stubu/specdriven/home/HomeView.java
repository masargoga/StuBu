package com.stubu.specdriven.home;

import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

/**
 * Home dashboard shown after login. Its content varies by role; later use cases add the time
 * recording and approval features here.
 */
@Route(value = "", layout = MainLayout.class)
@PermitAll
public class HomeView extends VerticalLayout implements HasDynamicTitle, LocaleChangeObserver {

    /** The signed-in employee, or {@code null} when the session was not established by an OIDC login. */
    private final EmployeePrincipal employee;

    public HomeView(AuthenticationContext authenticationContext) {
        addClassName("home-view");
        setPadding(true);
        employee = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance)
                .map(EmployeePrincipal.class::cast)
                .orElse(null);
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        removeAll();
        if (employee != null) {
            add(dashboardFor(employee));
        }
    }

    private VerticalLayout dashboardFor(EmployeePrincipal current) {
        H2 welcome = new H2(getTranslation("home.welcome", current.getFullName()));
        welcome.addClassName("home-welcome");

        Role role = current.getRole();
        Badge roleBadge = new Badge(getTranslation("role." + role.name()));
        roleBadge.setTestId("role-badge");
        Paragraph description = new Paragraph(getTranslation("home.role." + role.name()));
        description.addClassName("home-description");

        VerticalLayout dashboard = new VerticalLayout(welcome, roleBadge, description);
        dashboard.setPadding(false);
        return dashboard;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("home.title");
    }
}
