package com.stubu.specdriven.base;

import com.stubu.specdriven.admin.EmployeeManagementView;
import com.stubu.specdriven.admin.PublicHolidayView;
import com.stubu.specdriven.approval.ApprovalsView;
import com.stubu.specdriven.approval.EmployeesView;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.home.HomeView;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheetView;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

/**
 * Application shell for authenticated users: title, who is signed in, sign out, and the navigation drawer
 * (Today and My Timesheet for everybody; Approvals and Employees for managers; Employees for administrators).
 */
@PermitAll
public class MainLayout extends AppLayout implements LocaleChangeObserver {

    private final H1 title = new H1();
    private final Button signOut;
    private final SideNav navigation = new SideNav();
    private final SideNavItem today = new SideNavItem("", HomeView.class, VaadinIcon.CLOCK.create());
    private final SideNavItem timesheet = new SideNavItem("", MonthlyTimesheetView.class,
            VaadinIcon.CALENDAR.create());
    private final SideNavItem approvals = new SideNavItem("", ApprovalsView.class,
            VaadinIcon.CHECK_SQUARE_O.create());
    private final SideNavItem employees = new SideNavItem("", EmployeesView.class, VaadinIcon.USERS.create());
    private final SideNavItem manageEmployees = new SideNavItem("", EmployeeManagementView.class,
            VaadinIcon.USERS.create());
    private final SideNavItem holidays = new SideNavItem("", PublicHolidayView.class,
            VaadinIcon.CALENDAR_O.create());
    private final DrawerToggle drawerToggle = new DrawerToggle();

    public MainLayout(AuthenticationContext authenticationContext) {
        title.addClassName("app-title");

        HorizontalLayout user = new HorizontalLayout();
        user.addClassName("app-user");
        user.setAlignItems(FlexComponent.Alignment.CENTER);
        Role role = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getRole).orElse(Role.EMPLOYEE);
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
        drawerToggle.setTestId("drawer-toggle");
        addToNavbar(drawerToggle, navbar);

        today.setTestId("nav-today");
        timesheet.setTestId("nav-timesheet");
        approvals.setTestId("nav-approvals");
        employees.setTestId("nav-employees");
        manageEmployees.setTestId("nav-employees");
        holidays.setTestId("nav-holidays");
        navigation.addItem(today, timesheet);
        if (role == Role.MANAGER) {
            navigation.addItem(approvals, employees);
        }
        if (role == Role.ADMIN) {
            navigation.addItem(manageEmployees, holidays);
        }
        navigation.addClassName("app-navigation");
        addToDrawer(new Scroller(navigation));
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        title.setText(getTranslation("app.title"));
        signOut.setText(getTranslation("app.signOut"));
        today.setLabel(getTranslation("nav.today"));
        timesheet.setLabel(getTranslation("nav.timesheet"));
        approvals.setLabel(getTranslation("nav.approvals"));
        employees.setLabel(getTranslation("nav.employees"));
        manageEmployees.setLabel(getTranslation("nav.employees"));
        holidays.setLabel(getTranslation("nav.holidays"));
        navigation.getElement().setAttribute("aria-label", getTranslation("nav.label"));
        drawerToggle.setAriaLabel(getTranslation("nav.toggle"));
    }
}
