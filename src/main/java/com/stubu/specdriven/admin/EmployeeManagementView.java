package com.stubu.specdriven.admin;

import static com.stubu.specdriven.base.TableCells.cell;

import com.stubu.specdriven.base.BrowserTimeZone;
import com.stubu.specdriven.base.DialogError;
import com.stubu.specdriven.base.LoadErrorBox;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.base.MessageBox;
import com.stubu.specdriven.employee.Choice;
import com.stubu.specdriven.employee.DeactivationRefusedException;
import com.stubu.specdriven.employee.EmployeeAdminService;
import com.stubu.specdriven.employee.EmployeeNotFoundException;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.select.Select;
import com.stubu.specdriven.admin.EmployeeOverviewService.Query;
import com.stubu.specdriven.admin.EmployeeOverviewService.Sort;
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
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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
    private final transient EmployeeOverviewService overview;
    private final transient TimeEntryService entryService;
    private final Long adminId;
    private ZoneId zone;
    private boolean anyEmployees = true;
    private boolean ascending = true;
    private boolean updatingFilters;
    private int page;
    private transient EmployeeOverviewService.Page result = new EmployeeOverviewService.Page(List.of(), 0, 0, 1,
            true);
    private transient List<Choice> departments = List.of();
    private transient List<Choice> regions = List.of();
    private transient List<Choice> managers = List.of();
    private boolean loadFailed;
    private Message message;

    private final H2 heading = new H2();
    private final Button add = new Button(VaadinIcon.PLUS.create());
    private final MessageBox messageBox = new MessageBox();
    private final LoadErrorBox loadErrorBox = new LoadErrorBox(this::refresh);
    private final TextField search = new TextField();
    private final Select<Boolean> status = new Select<>();
    private final Select<Role> role = new Select<>();
    private final Select<Choice> department = new Select<>();
    private final Select<Sort> sort = new Select<>();
    private final Button direction = new Button();
    private final Div filters = new Div();
    private final Span count = new Span();
    private final Div emptyHint = new Div();
    private final Div list = new Div();
    private final Button previous = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final Button next = new Button(VaadinIcon.ANGLE_RIGHT.create());
    private final Span pageInfo = new Span();
    private final Div pager = new Div();

    public EmployeeManagementView(AuthenticationContext authenticationContext, EmployeeAdminService service,
            EmployeeOverviewService overview, TimeEntryService entryService) {
        this.service = service;
        this.overview = overview;
        this.entryService = entryService;
        this.zone = entryService.defaultZone();
        this.adminId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);

        addClassNames("timesheet-view", "manage-view");
        setPadding(true);

        add.setTestId("add-employee");
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClassName("manage-add");
        add.addClickListener(event -> openForm(null));

        search.setTestId("employee-search");
        search.setClearButtonVisible(true);
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addClassName("manage-search");
        search.addValueChangeListener(event -> filterChanged());
        status.setTestId("filter-status");
        status.setEmptySelectionAllowed(true);
        status.setItems(Boolean.TRUE, Boolean.FALSE);
        status.setItemLabelGenerator(value -> value == null ? getTranslation("manage.filter.all")
                : getTranslation(value ? "manage.active" : "manage.inactive"));
        status.addValueChangeListener(event -> filterChanged());
        role.setTestId("filter-role");
        role.setEmptySelectionAllowed(true);
        role.setItems(Role.values());
        role.setItemLabelGenerator(value -> value == null ? getTranslation("manage.filter.all")
                : getTranslation("role." + value.name()));
        role.addValueChangeListener(event -> filterChanged());
        department.setTestId("filter-department");
        department.setEmptySelectionAllowed(true);
        department.setItemLabelGenerator(value -> value == null ? getTranslation("manage.filter.all") : value.label());
        department.addValueChangeListener(event -> filterChanged());
        sort.setTestId("sort-by");
        sort.setItems(Sort.values());
        sort.setValue(Sort.NAME);
        sort.setItemLabelGenerator(value -> getTranslation("manage.sort." + value.name()));
        sort.addValueChangeListener(event -> filterChanged());
        direction.setTestId("sort-direction");
        direction.addThemeVariants(ButtonVariant.TERTIARY);
        direction.addClickListener(event -> {
            ascending = !ascending;
            filterChanged();
        });
        filters.add(search, status, role, department, sort, direction);
        filters.addClassName("manage-filters");
        count.setTestId("employee-count");
        count.addClassName("audit-summary");
        emptyHint.addClassName("time-empty");
        emptyHint.setTestId("no-employees");
        list.addClassNames("approvals-list", "manage-list");
        list.setTestId("employees");
        list.getElement().setAttribute("role", "table");

        previous.setTestId("employees-previous");
        previous.addThemeVariants(ButtonVariant.TERTIARY);
        previous.addClickListener(event -> goTo(page - 1));
        next.setTestId("employees-next");
        next.addThemeVariants(ButtonVariant.TERTIARY);
        next.addClickListener(event -> goTo(page + 1));
        pageInfo.setTestId("employees-page");
        pager.add(previous, pageInfo, next);
        pager.addClassName("audit-pager");

        add(heading, add, messageBox, loadErrorBox, filters, count, emptyHint, list, pager);
        load();
    }

    /** The browser's time zone decides how the last login times are shown. */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        BrowserTimeZone.detect(attachEvent.getUI(), zone, browserZone -> {
            zone = browserZone;
            render();
        });
    }

    private void filterChanged() {
        if (!updatingFilters) {
            page = 0;
            refresh();
        }
    }

    private void goTo(int target) {
        page = Math.max(target, 0);
        refresh();
    }

    private Query query() {
        return new Query(search.getValue(), status.getValue(), role.getValue(),
                department.getValue() == null ? null : department.getValue().id(), sort.getValue(), ascending);
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
            result = overview.page(adminId, query(), page);
            page = result.page(); // the list shrank: the last page
            anyEmployees = result.anyEmployees();
            departments = service.departments(adminId);
            regions = service.regions(adminId);
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
        search.setPlaceholder(getTranslation("manage.search.placeholder"));
        updatingFilters = true;
        status.setLabel(getTranslation("manage.status"));
        status.setEmptySelectionCaption(getTranslation("manage.filter.all"));
        role.setLabel(getTranslation("employees.role"));
        role.setEmptySelectionCaption(getTranslation("manage.filter.all"));
        Choice chosen = department.getValue();
        department.setLabel(getTranslation("manage.department"));
        department.setEmptySelectionCaption(getTranslation("manage.filter.all"));
        department.setItems(departments);
        department.setValue(chosen != null && departments.contains(chosen) ? chosen : null);
        sort.setLabel(getTranslation("manage.sort"));
        direction.setIcon((ascending ? VaadinIcon.ARROW_UP : VaadinIcon.ARROW_DOWN).create());
        direction.setText(getTranslation(ascending ? "manage.sort.ascending" : "manage.sort.descending"));
        updatingFilters = false;

        messageBox.show(message == null ? null : getTranslation(message.key(), message.parameter()), message != null && message.error());

        loadErrorBox.update(loadFailed, getTranslation("manage.loadFailed"), getTranslation("time.retry"));
        add.setEnabled(!loadFailed);
        filters.setVisible(!loadFailed);

        List<EmployeeRow> shown = result.rows();
        count.setText(getTranslation("manage.count", result.total()));
        pager.setVisible(!loadFailed && result.pages() > 1);
        pageInfo.setText(getTranslation("audit.page", result.page() + 1, result.pages()));
        previous.setEnabled(result.page() > 0);
        next.setEnabled(result.page() + 1 < result.pages());
        previous.getElement().setAttribute("aria-label", getTranslation("audit.previous"));
        next.getElement().setAttribute("aria-label", getTranslation("audit.next"));
        count.setVisible(!loadFailed && !shown.isEmpty());
        emptyHint.setText(getTranslation(anyEmployees ? "manage.noMatch" : "manage.none"));
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
                cell("columnheader", getTranslation("manage.lastLogin"), null),
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
        Button edit = action("manage.edit", "edit-employee", employee, ButtonVariant.TERTIARY);
        edit.addClickListener(event -> openForm(employee));
        actions.add(edit);
        Button details = action("manage.details", "employee-details", employee, ButtonVariant.TERTIARY);
        details.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(EmployeeDetailView.class,
                employee.id())));
        actions.add(details);
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
                statusCell, cell("cell", lastLogin(employee.lastLoginAt()), getTranslation("manage.lastLogin")), actions);
        row.addClassNames("approval-row", "manage-row");
        row.setClassName("employee-inactive", !employee.active());
        row.setTestId("manage-row");
        row.getElement().setAttribute("role", "row");
        return row;
    }

    private String lastLogin(Instant at) {
        return at == null ? getTranslation("manage.lastLogin.never") : DateTimeFormatter.ofLocalizedDateTime(
                FormatStyle.MEDIUM).withLocale(getLocale()).withZone(zone).format(at);
    }

    private Button action(String key, String testId, EmployeeRow employee, ButtonVariant... variants) {
        Button button = new Button(getTranslation(key));
        button.setTestId(testId);
        button.addThemeVariants(variants);
        button.addClassName("review-button");
        button.getElement().setAttribute("aria-label", getTranslation(key + ".label", employee.fullName()));
        return button;
    }

    // --- add and edit ------------------------------------------------------------------------------

    private void openForm(EmployeeRow existing) {
        List<Choice> possibleManagers = new ArrayList<>(managers);
        if (existing != null && existing.managerId() != null && possibleManagers.stream()
                .noneMatch(choice -> choice.id() == existing.managerId())) {
            // The current manager may be inactive: keep them, or saving would silently drop the manager.
            possibleManagers.add(new Choice(existing.managerId(), existing.managerName()));
        }
        new EmployeeFormDialog(service, adminId).open(this, existing, departments, regions, possibleManagers, result -> {
            message = switch (result.outcome()) {
                case CREATED -> new Message("manage.created", result.name(), false);
                case UPDATED -> new Message("manage.updated", result.name(), false);
                case GONE -> new Message("manage.gone", "", true);
                case CONFLICT -> new Message("manage.conflict", result.name(), true);
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
        DialogError error = new DialogError();
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
