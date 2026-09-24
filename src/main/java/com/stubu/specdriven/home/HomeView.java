package com.stubu.specdriven.home;

import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.stubu.specdriven.timetracking.TimeTrackingPanel;
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
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;

/**
 * Home page shown after login: who is signed in, and the time recording panel. Every role includes the
 * EMPLOYEE role, so everybody records their own time here.
 */
@Route(value = "", layout = MainLayout.class)
@PermitAll
public class HomeView extends VerticalLayout implements HasDynamicTitle, LocaleChangeObserver {

    /** The signed-in employee, or {@code null} when the session was not established by an OIDC login. */
    private final EmployeePrincipal employee;
    private final VerticalLayout header = new VerticalLayout();

    public HomeView(AuthenticationContext authenticationContext, TimeEntryService timeEntryService,
            @Value("${stubu.time-tracking.refresh-interval:PT30S}") Duration refreshInterval) {
        addClassName("home-view");
        setPadding(true);
        header.setPadding(false);
        add(header);

        employee = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance)
                .map(EmployeePrincipal.class::cast)
                .orElse(null);
        if (employee != null && employee.getEmployeeId() != null) {
            add(new TimeTrackingPanel(timeEntryService, employee.getEmployeeId(), refreshInterval));
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        header.removeAll();
        if (employee != null) {
            header.add(headerFor(employee));
        }
    }

    private VerticalLayout headerFor(EmployeePrincipal current) {
        H2 welcome = new H2(getTranslation("home.welcome", current.getFullName()));
        welcome.addClassName("home-welcome");

        Role role = current.getRole();
        Badge roleBadge = new Badge(getTranslation("role." + role.name()));
        roleBadge.setTestId("role-badge");
        Paragraph description = new Paragraph(getTranslation("home.role." + role.name()));
        description.addClassName("home-description");

        VerticalLayout box = new VerticalLayout(welcome, roleBadge, description);
        box.setPadding(false);
        return box;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("home.title");
    }
}
