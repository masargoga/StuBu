package com.stubu.specdriven.approval;

import com.stubu.specdriven.base.FlashMessage;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * The manager's list of employees (UC-009): the employees they may look at, with a search by name or email
 * address. From here a manager opens the timesheet of an employee.
 */
@Route(value = EmployeesView.ROUTE, layout = MainLayout.class)
@RolesAllowed({ "MANAGER", "ADMIN" })
public class EmployeesView extends VerticalLayout implements HasDynamicTitle, LocaleChangeObserver {

    public static final String ROUTE = "employees";
    private static final Logger log = LoggerFactory.getLogger(EmployeesView.class);

    private final transient TimesheetReviewService service;
    private final Long reviewerId;
    private transient List<EmployeeSummary> employees = List.of();
    private boolean loadFailed;
    private FlashMessage message;

    private final H2 heading = new H2();
    private final Div messageBox = new Div();
    private final Div loadErrorBox = new Div();
    private final Span loadError = new Span();
    private final Button retry = new Button();
    private final TextField search = new TextField();
    private final Div emptyHint = new Div();
    private final Div list = new Div();

    public EmployeesView(AuthenticationContext authenticationContext, TimesheetReviewService service) {
        this.service = service;
        this.reviewerId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);
        message = FlashMessage.take();

        addClassNames("timesheet-view", "employees-view");
        setPadding(true);

        messageBox.addClassName("time-message");
        messageBox.setVisible(false);
        var errorIcon = VaadinIcon.WARNING.create();
        errorIcon.getElement().setAttribute("aria-hidden", "true");
        retry.setTestId("retry");
        retry.addThemeVariants(ButtonVariant.PRIMARY);
        retry.addClickListener(event -> refresh());
        loadErrorBox.add(errorIcon, loadError, retry);
        loadErrorBox.addClassNames("time-message", "time-message-error", "time-load-error");
        loadErrorBox.getElement().setAttribute("role", "alert");
        loadErrorBox.setVisible(false);

        search.setTestId("employee-search");
        search.setClearButtonVisible(true);
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setWidthFull();
        search.setMaxWidth("28rem");
        search.addValueChangeListener(event -> render());
        emptyHint.addClassName("time-empty");
        emptyHint.setTestId("no-employees");
        list.addClassName("approvals-list");
        list.setTestId("employees");
        list.getElement().setAttribute("role", "table");

        add(heading, messageBox, loadErrorBox, search, emptyHint, list);
        load();
    }

    public void refresh() {
        load();
        render();
    }

    private void load() {
        if (reviewerId == null) {
            return;
        }
        try {
            employees = service.employees(reviewerId, ReviewScope.DIRECT_REPORTS);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load the employees of reviewer {}", reviewerId, e);
            loadFailed = true;
        }
    }

    @Override
    public String getPageTitle() {
        return getTranslation("employees.title");
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    private void render() {
        Locale locale = getLocale();
        heading.setText(getTranslation("employees.title"));
        search.setLabel(getTranslation("employees.search"));
        search.setPlaceholder(getTranslation("employees.search.placeholder"));

        if (message == null) {
            messageBox.setVisible(false);
        } else {
            var icon = (message.error() ? VaadinIcon.WARNING : VaadinIcon.CHECK_CIRCLE).create();
            icon.getElement().setAttribute("aria-hidden", "true");
            messageBox.removeAll();
            messageBox.add(icon, new Span(getTranslation(message.key())));
            messageBox.setClassName("time-message-error", message.error());
            messageBox.getElement().setAttribute("role", message.error() ? "alert" : "status");
            messageBox.setVisible(true);
        }

        loadError.setText(getTranslation("employees.loadFailed"));
        retry.setText(getTranslation("time.retry"));
        loadErrorBox.setVisible(loadFailed);
        search.setVisible(!loadFailed);

        String query = search.getValue() == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        List<EmployeeSummary> shown = employees.stream().filter(employee -> query.isEmpty()
                || employee.fullName().toLowerCase(Locale.ROOT).contains(query)
                || employee.email().toLowerCase(Locale.ROOT).contains(query)).toList();
        emptyHint.setText(getTranslation(employees.isEmpty() ? "employees.none" : "employees.noMatch"));
        emptyHint.setVisible(!loadFailed && shown.isEmpty());
        list.setVisible(!loadFailed && !shown.isEmpty());
        if (!list.isVisible()) {
            return;
        }

        list.removeAll();
        list.getElement().setAttribute("aria-label", getTranslation("employees.title"));
        Div head = new Div(cell("columnheader", getTranslation("employees.name"), null),
                cell("columnheader", getTranslation("employees.role"), null),
                cell("columnheader", getTranslation("employees.email"), null),
                cell("columnheader", getTranslation("approvals.action"), null));
        head.addClassName("approval-head");
        head.getElement().setAttribute("role", "row");
        list.add(head);
        for (EmployeeSummary employee : shown) {
            Span name = cell("cell", employee.fullName(), null);
            name.addClassName("approval-employee");
            String role = getTranslation("role." + employee.role().name())
                    + (employee.active() ? "" : " (" + getTranslation("employees.inactive") + ")");
            Div action = new Div(viewButton(employee));
            action.addClassName("approval-cell");
            action.getElement().setAttribute("role", "cell");
            Div row = new Div(name, cell("cell", role, getTranslation("employees.role")),
                    cell("cell", employee.email(), getTranslation("employees.email")), action);
            row.addClassName("approval-row");
            row.setTestId("employee-row");
            row.getElement().setAttribute("role", "row");
            list.add(row);
        }
    }

    private Button viewButton(EmployeeSummary employee) {
        Button view = new Button(getTranslation("employees.view"));
        view.setTestId("view-timesheet");
        view.addThemeVariants(ButtonVariant.PRIMARY);
        view.addClassName("review-button");
        view.getElement().setAttribute("aria-label", getTranslation("employees.view.label", employee.fullName()));
        view.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(EmployeeTimesheetView.class,
                employee.id())));
        return view;
    }

    /** A table cell; on narrow screens the label is shown in front of the value (see styles.css). */
    private static Span cell(String role, String text, String label) {
        Span cell = new Span(text);
        cell.addClassName("approval-cell");
        cell.getElement().setAttribute("role", role);
        if (label != null) {
            cell.getElement().setAttribute("data-label", label);
        }
        return cell;
    }
}
