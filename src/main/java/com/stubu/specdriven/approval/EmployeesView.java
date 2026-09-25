package com.stubu.specdriven.approval;

import static com.stubu.specdriven.base.TableCells.cell;

import com.stubu.specdriven.base.FlashMessage;
import com.stubu.specdriven.base.LoadErrorBox;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.base.MessageBox;
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
    private ReviewScope scope = ReviewScopePreference.get();
    private transient ScopeOptions options;
    private FlashMessage message;

    private final H2 heading = new H2();
    private final ScopeSwitcher scopeSwitcher = new ScopeSwitcher();
    private final MessageBox messageBox = new MessageBox();
    private final LoadErrorBox loadErrorBox = new LoadErrorBox(this::refresh);
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

        add(heading, messageBox, loadErrorBox, scopeSwitcher, search, emptyHint, list);
        scopeSwitcher.addScopeListener(this::switchScope);
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
            options = service.scopeOptions(reviewerId);
            if (scope == ReviewScope.DEPARTMENT && !options.departmentAvailable()) {
                scope = ReviewScope.DIRECT_REPORTS;
            }
            employees = service.employees(reviewerId, scope);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load the employees of reviewer {}", reviewerId, e);
            loadFailed = true;
        }
    }

    /** The manager picked another scope: show it, or keep the previous one if it cannot be loaded (AF-3). */
    private void switchScope(ReviewScope newScope) {
        try {
            employees = service.employees(reviewerId, newScope);
            scope = newScope;
            ReviewScopePreference.set(scope);
            message = null;
        } catch (DataAccessException e) {
            log.error("Could not load the employees of reviewer {} for {}", reviewerId, newScope, e);
            message = new FlashMessage("scope.loadFailed", true);
        }
        render();
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

        messageBox.show(message == null ? null : getTranslation(message.key()), message != null && message.error());

        scopeSwitcher.setVisible(!loadFailed && options != null);
        if (options != null) {
            scopeSwitcher.update(options, scope);
        }
        loadErrorBox.update(loadFailed, getTranslation("employees.loadFailed"), getTranslation("time.retry"));
        search.setVisible(!loadFailed);

        String query = search.getValue() == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        List<EmployeeSummary> shown = employees.stream().filter(employee -> query.isEmpty()
                || employee.fullName().toLowerCase(Locale.ROOT).contains(query)
                || employee.email().toLowerCase(Locale.ROOT).contains(query)).toList();
        emptyHint.setText(getTranslation(!employees.isEmpty() ? "employees.noMatch"
                : scope == ReviewScope.DEPARTMENT ? "employees.noneDepartment" : "employees.none"));
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

}
