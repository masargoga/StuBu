package com.stubu.specdriven.admin;

import com.stubu.specdriven.approval.EmployeeTimesheetView;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.employee.Choice;
import com.stubu.specdriven.employee.DeactivationRefusedException;
import com.stubu.specdriven.employee.EmployeeAdminService;
import com.stubu.specdriven.employee.EmployeeNotFoundException;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * Employee management for administrators (UC-011): all employees with their role, department and manager, and the
 * actions to add, edit and deactivate them. Deactivated employees stay in the list, marked as inactive.
 */
@Route(value = EmployeeManagementView.ROUTE, layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class EmployeeManagementView extends VerticalLayout implements HasDynamicTitle, LocaleChangeObserver {

    public static final String ROUTE = "admin/employees";
    private static final Logger log = LoggerFactory.getLogger(EmployeeManagementView.class);

    /** The last message, kept as key and parameter so it can be translated again. */
    private record Message(String key, String parameter, boolean error) {
    }

    private final transient EmployeeAdminService service;
    private final Long adminId;
    private transient List<EmployeeRow> employees = List.of();
    private transient List<Choice> departments = List.of();
    private transient List<Choice> managers = List.of();
    private boolean loadFailed;
    private Message message;

    private final H2 heading = new H2();
    private final Button add = new Button(VaadinIcon.PLUS.create());
    private final Div messageBox = new Div();
    private final Div loadErrorBox = new Div();
    private final Span loadError = new Span();
    private final Button retry = new Button();
    private final TextField search = new TextField();
    private final Div emptyHint = new Div();
    private final Div list = new Div();

    public EmployeeManagementView(AuthenticationContext authenticationContext, EmployeeAdminService service) {
        this.service = service;
        this.adminId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);

        addClassNames("timesheet-view", "manage-view");
        setPadding(true);

        add.setTestId("add-employee");
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClassName("manage-add");
        add.addClickListener(event -> openForm(null));
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
        search.addClassName("manage-search");
        search.addValueChangeListener(event -> render());
        emptyHint.addClassName("time-empty");
        emptyHint.setTestId("no-employees");
        list.addClassNames("approvals-list", "manage-list");
        list.setTestId("employees");
        list.getElement().setAttribute("role", "table");

        add(heading, add, messageBox, loadErrorBox, search, emptyHint, list);
        load();
    }

    public void refresh() {
        load();
        render();
    }

    private void load() {
        if (adminId == null) {
            return;
        }
        try {
            employees = service.list(adminId);
            departments = service.departments(adminId);
            managers = service.managerCandidates(adminId);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load the employees for administrator {}", adminId, e);
            loadFailed = true;
        }
    }

    @Override
    public String getPageTitle() {
        return getTranslation("manage.title");
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    // --- rendering ---------------------------------------------------------------------------------

    private void render() {
        heading.setText(getTranslation("manage.title"));
        add.setText(getTranslation("manage.add"));
        search.setLabel(getTranslation("employees.search"));
        search.setPlaceholder(getTranslation("employees.search.placeholder"));

        if (message == null) {
            messageBox.setVisible(false);
        } else {
            var icon = (message.error() ? VaadinIcon.WARNING : VaadinIcon.CHECK_CIRCLE).create();
            icon.getElement().setAttribute("aria-hidden", "true");
            messageBox.removeAll();
            messageBox.add(icon, new Span(getTranslation(message.key(), message.parameter())));
            messageBox.setClassName("time-message-error", message.error());
            messageBox.getElement().setAttribute("role", message.error() ? "alert" : "status");
            messageBox.setVisible(true);
        }

        loadError.setText(getTranslation("manage.loadFailed"));
        retry.setText(getTranslation("time.retry"));
        loadErrorBox.setVisible(loadFailed);
        add.setEnabled(!loadFailed);
        search.setVisible(!loadFailed);

        String query = search.getValue() == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        List<EmployeeRow> shown = employees.stream().filter(employee -> query.isEmpty()
                || employee.fullName().toLowerCase(Locale.ROOT).contains(query)
                || employee.email().toLowerCase(Locale.ROOT).contains(query)).toList();
        emptyHint.setText(getTranslation(employees.isEmpty() ? "manage.none" : "employees.noMatch"));
        emptyHint.setVisible(!loadFailed && shown.isEmpty());
        list.setVisible(!loadFailed && !shown.isEmpty());
        if (!list.isVisible()) {
            return;
        }

        list.removeAll();
        list.getElement().setAttribute("aria-label", getTranslation("manage.title"));
        Div head = new Div(cell("columnheader", getTranslation("employees.name"), null),
                cell("columnheader", getTranslation("employees.email"), null),
                cell("columnheader", getTranslation("employees.role"), null),
                cell("columnheader", getTranslation("manage.department"), null),
                cell("columnheader", getTranslation("manage.manager"), null),
                cell("columnheader", getTranslation("manage.status"), null),
                cell("columnheader", getTranslation("approvals.action"), null));
        head.addClassName("approval-head");
        head.getElement().setAttribute("role", "row");
        list.add(head);
        for (EmployeeRow employee : shown) {
            list.add(row(employee));
        }
    }

    private Div row(EmployeeRow employee) {
        Span name = cell("cell", employee.fullName(), null);
        name.addClassName("approval-employee");
        Badge status = new Badge(getTranslation(employee.active() ? "manage.active" : "manage.inactive"));
        status.addThemeVariants(employee.active() ? BadgeVariant.SUCCESS : BadgeVariant.CONTRAST);
        status.setTestId("employee-status");
        Span statusCell = new Span(status);
        statusCell.addClassName("approval-cell");
        statusCell.getElement().setAttribute("role", "cell");
        statusCell.getElement().setAttribute("data-label", getTranslation("manage.status"));

        Div actions = new Div();
        actions.addClassNames("approval-cell", "manage-actions");
        actions.getElement().setAttribute("role", "cell");
        Button edit = action("manage.edit", "edit-employee", employee, ButtonVariant.PRIMARY);
        edit.addClickListener(event -> openForm(employee));
        actions.add(edit);
        Button timesheets = action("manage.timesheets", "employee-timesheets", employee, ButtonVariant.TERTIARY);
        timesheets.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(EmployeeTimesheetView.class,
                employee.id())));
        actions.add(timesheets);
        if (employee.active()) {
            Button deactivate = action("manage.deactivate", "deactivate-employee", employee, ButtonVariant.ERROR,
                    ButtonVariant.TERTIARY);
            deactivate.addClickListener(event -> openDeactivate(employee));
            actions.add(deactivate);
        }

        Div row = new Div(name,
                cell("cell", employee.email(), getTranslation("employees.email")),
                cell("cell", getTranslation("role." + employee.role().name()), getTranslation("employees.role")),
                cell("cell", employee.departmentName(), getTranslation("manage.department")),
                cell("cell", employee.managerName() == null ? getTranslation("manage.manager.none")
                        : employee.managerName(), getTranslation("manage.manager")),
                statusCell, actions);
        row.addClassNames("approval-row", "manage-row");
        row.setClassName("employee-inactive", !employee.active());
        row.setTestId("manage-row");
        row.getElement().setAttribute("role", "row");
        return row;
    }

    private Button action(String key, String testId, EmployeeRow employee, ButtonVariant... variants) {
        Button button = new Button(getTranslation(key));
        button.setTestId(testId);
        button.addThemeVariants(variants);
        button.addClassName("review-button");
        button.getElement().setAttribute("aria-label", getTranslation(key + ".label", employee.fullName()));
        return button;
    }

    /** A table cell; on narrow screens the label is shown in front of the value (see styles.css). */
    private static Span cell(String role, String text, String label) {
        Span cell = new Span(text == null ? "" : text);
        cell.addClassName("approval-cell");
        cell.getElement().setAttribute("role", role);
        if (label != null) {
            cell.getElement().setAttribute("data-label", label);
        }
        return cell;
    }

    // --- add and edit ------------------------------------------------------------------------------

    private void openForm(EmployeeRow existing) {
        List<Choice> possibleManagers = new ArrayList<>(managers);
        if (existing != null && existing.managerId() != null && possibleManagers.stream()
                .noneMatch(choice -> choice.id() == existing.managerId())) {
            // The current manager may be inactive: keep them, or saving would silently drop the manager.
            possibleManagers.add(new Choice(existing.managerId(), existing.managerName()));
        }
        new EmployeeFormDialog(service, adminId).open(this, existing, departments, possibleManagers, result -> {
            message = switch (result.outcome()) {
                case CREATED -> new Message("manage.created", result.name(), false);
                case UPDATED -> new Message("manage.updated", result.name(), false);
                case GONE -> new Message("manage.gone", "", true);
            };
            refresh();
        });
    }

    // --- deactivate --------------------------------------------------------------------------------

    private void openDeactivate(EmployeeRow employee) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(34rem, 92vw)");
        dialog.setHeaderTitle(getTranslation("manage.deactivate.title"));
        Paragraph question = new Paragraph(getTranslation("manage.deactivate.confirm", employee.fullName()));
        question.addClassName("time-dialog-text");
        TextField reason = new TextField(getTranslation("manage.deactivate.reason"));
        reason.setTestId("deactivate-reason");
        reason.setMaxLength(500);
        reason.setWidthFull();
        Div error = new Div();
        error.addClassName("time-dialog-error");
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);
        Div body = new Div(question, reason, error);
        body.addClassNames("time-dialog-content", "review-dialog-content");
        dialog.add(body);

        Button cancel = new Button(getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("deactivate-cancel");
        cancel.addClassName("time-dialog-button");
        Button confirm = new Button(getTranslation("manage.deactivate"));
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        confirm.setTestId("deactivate-confirm");
        confirm.addClassName("time-dialog-button");
        confirm.addClickListener(event -> {
            try {
                service.deactivate(adminId, employee.id(), reason.getValue());
                message = new Message("manage.deactivated", employee.fullName(), false);
            } catch (DeactivationRefusedException refused) {
                error.setText(getTranslation("manage.deactivate.refused." + refused.getReason().name()));
                error.setVisible(true);
                return;
            } catch (EmployeeNotFoundException gone) {
                message = new Message("manage.gone", "", true);
            } catch (DataAccessException e) {
                log.error("Deactivating employee {} failed for administrator {}", employee.id(), adminId, e);
                error.setText(getTranslation("manage.saveFailed"));
                error.setVisible(true); // stays open: Deactivate again to retry
                return;
            }
            dialog.close();
            refresh();
        });
        dialog.getFooter().add(cancel, confirm);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }
}
