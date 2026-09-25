package com.stubu.specdriven.admin;

import com.stubu.specdriven.approval.EmployeeTimesheetView;
import com.stubu.specdriven.base.FlashMessage;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.employee.EmployeeNotFoundException;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * One employee for administrators (UC-014): the full record and the list of all their timesheets, whatever the
 * status. A timesheet opens in the read-only timesheet view (days, totals, history, comments, single days). Nothing
 * can be changed here; editing is employee management (UC-011).
 */
@Route(value = "admin/employees/details", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class EmployeeDetailView extends VerticalLayout implements HasUrlParameter<Long>, HasDynamicTitle,
        LocaleChangeObserver {

    private static final Logger log = LoggerFactory.getLogger(EmployeeDetailView.class);

    private final transient EmployeeOverviewService service;
    private final Long adminId;
    private ZoneId zone;
    private Long employeeId;
    private transient EmployeeOverviewService.Details details;
    private boolean loadFailed;

    private final Button back = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final H2 heading = new H2();
    private final Badge statusBadge = new Badge();
    private final Div loadErrorBox = new Div();
    private final Span loadError = new Span();
    private final Button retry = new Button();
    private final Div info = new Div();
    private final H3 timesheetsHeading = new H3();
    private final Div emptyHint = new Div();
    private final Div list = new Div();

    public EmployeeDetailView(AuthenticationContext authenticationContext, EmployeeOverviewService service,
            TimeEntryService entryService) {
        this.service = service;
        this.zone = entryService.defaultZone();
        this.adminId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);

        addClassNames("timesheet-view", "manage-view", "detail-view");
        setPadding(true);

        back.setTestId("back");
        back.addThemeVariants(ButtonVariant.TERTIARY);
        back.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(EmployeeManagementView.class)));
        statusBadge.setTestId("employee-status");
        HorizontalLayout title = new HorizontalLayout(heading, statusBadge);
        title.setAlignItems(FlexComponent.Alignment.CENTER);
        title.setPadding(false);
        title.setWrap(true);

        var errorIcon = VaadinIcon.WARNING.create();
        errorIcon.getElement().setAttribute("aria-hidden", "true");
        retry.setTestId("retry");
        retry.addThemeVariants(ButtonVariant.PRIMARY);
        retry.addClickListener(event -> refresh());
        loadErrorBox.add(errorIcon, loadError, retry);
        loadErrorBox.addClassNames("time-message", "time-message-error", "time-load-error");
        loadErrorBox.getElement().setAttribute("role", "alert");
        loadErrorBox.setVisible(false);
        info.addClassName("detail-info");
        info.setTestId("employee-info");
        emptyHint.addClassName("time-empty");
        emptyHint.setTestId("no-timesheets");
        list.addClassNames("approvals-list", "detail-timesheets");
        list.setTestId("employee-timesheets");
        list.getElement().setAttribute("role", "table");

        add(back, title, loadErrorBox, info, timesheetsHeading, emptyHint, list);
    }

    @Override
    public void setParameter(BeforeEvent event, Long employeeId) {
        this.employeeId = employeeId;
        try {
            load();
        } catch (EmployeeNotFoundException gone) {
            FlashMessage.set("manage.gone", true);
            event.forwardTo(EmployeeManagementView.class);
            return;
        }
        if (isAttached()) {
            render();
        }
    }

    /** The browser's time zone decides how times are shown. */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        attachEvent.getUI().getPage().retrieveExtendedClientDetails(clientDetails -> {
            try {
                ZoneId browserZone = ZoneId.of(clientDetails.getTimeZoneId());
                if (!browserZone.equals(zone)) {
                    zone = browserZone;
                    render();
                }
            } catch (DateTimeException | NullPointerException unknownZone) {
                log.debug("Keeping the server time zone, the browser reported {}", clientDetails.getTimeZoneId());
            }
        });
    }

    private void refresh() {
        try {
            load();
        } catch (EmployeeNotFoundException gone) {
            FlashMessage.set("manage.gone", true);
            getUI().ifPresent(ui -> ui.navigate(EmployeeManagementView.class));
            return;
        }
        render();
    }

    private void load() {
        if (adminId == null || employeeId == null) {
            throw new EmployeeNotFoundException();
        }
        try {
            details = service.details(adminId, employeeId);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load employee {} for administrator {}", employeeId, adminId, e);
            loadFailed = true;
        }
    }

    @Override
    public String getPageTitle() {
        return getTranslation("employeeDetail.pageTitle");
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    // --- rendering ---------------------------------------------------------------------------------

    private void render() {
        Locale locale = getLocale();
        back.setText(getTranslation("employeeDetail.back"));
        back.getElement().setAttribute("aria-label", getTranslation("employeeDetail.back"));
        loadError.setText(getTranslation("employeeDetail.loadFailed"));
        retry.setText(getTranslation("time.retry"));
        loadErrorBox.setVisible(loadFailed);
        boolean loaded = !loadFailed && details != null;
        heading.setVisible(loaded);
        statusBadge.setVisible(loaded);
        info.setVisible(loaded);
        timesheetsHeading.setVisible(loaded);
        emptyHint.setVisible(false);
        list.setVisible(false);
        if (!loaded) {
            return;
        }

        EmployeeRow employee = details.employee();
        heading.setText(employee.fullName());
        statusBadge.setText(getTranslation(employee.active() ? "manage.active" : "manage.inactive"));
        statusBadge.getElement().setAttribute("theme", "");
        statusBadge.addThemeVariants(employee.active() ? BadgeVariant.SUCCESS : BadgeVariant.CONTRAST);
        info.removeAll();
        info.add(field("employees.email", employee.email()),
                field("employees.role", getTranslation("role." + employee.role().name())),
                field("manage.department", employee.departmentName()),
                field("manage.manager", employee.managerName() == null ? getTranslation("manage.manager.none")
                        : employee.managerName()),
                field("employeeDetail.created", dateTime(employee.createdAt(), locale)),
                field("manage.lastLogin", employee.lastLoginAt() == null ? getTranslation("manage.lastLogin.never")
                        : dateTime(employee.lastLoginAt(), locale)));

        timesheetsHeading.setText(getTranslation("employeeDetail.timesheets"));
        List<EmployeeOverviewService.TimesheetLine> lines = details.timesheets();
        emptyHint.setText(getTranslation("employeeDetail.noTimesheets"));
        emptyHint.setVisible(lines.isEmpty());
        list.setVisible(!lines.isEmpty());
        if (lines.isEmpty()) {
            return;
        }
        list.removeAll();
        list.getElement().setAttribute("aria-label", getTranslation("employeeDetail.timesheets"));
        Div head = new Div(cell("columnheader", getTranslation("employeeDetail.month"), null),
                cell("columnheader", getTranslation("manage.status"), null),
                cell("columnheader", getTranslation("employeeDetail.submitted"), null),
                cell("columnheader", getTranslation("employeeDetail.decided"), null),
                cell("columnheader", getTranslation("employeeDetail.note"), null),
                cell("columnheader", getTranslation("approvals.action"), null));
        head.addClassName("approval-head");
        head.getElement().setAttribute("role", "row");
        list.add(head);
        for (EmployeeOverviewService.TimesheetLine line : lines) {
            list.add(row(employee, line, locale));
        }
    }

    private Div row(EmployeeRow employee, EmployeeOverviewService.TimesheetLine line, Locale locale) {
        String month = line.period().getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " "
                + line.period().getYear();
        Span monthCell = cell("cell", month, null);
        monthCell.addClassName("approval-employee");
        Badge status = new Badge(getTranslation("timesheet.badge." + line.status().name()));
        status.setTestId("timesheet-status");
        switch (line.status()) {
            case DRAFT -> status.addThemeVariants(BadgeVariant.WARNING);
            case SUBMITTED -> status.addThemeVariants();
            case APPROVED -> status.addThemeVariants(BadgeVariant.SUCCESS);
            case REJECTED -> status.addThemeVariants(BadgeVariant.ERROR);
        }
        Span statusCell = new Span(status);
        statusCell.addClassName("approval-cell");
        statusCell.getElement().setAttribute("role", "cell");
        statusCell.getElement().setAttribute("data-label", getTranslation("manage.status"));
        Button view = new Button(getTranslation("employeeDetail.view"));
        view.setTestId("open-timesheet");
        view.addThemeVariants(ButtonVariant.PRIMARY);
        view.addClassName("review-button");
        view.getElement().setAttribute("aria-label", getTranslation("employeeDetail.view.label", month));
        view.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(EmployeeTimesheetView.class,
                employee.id(), QueryParameters.of("month", line.period().toString()))));
        Div action = new Div(view);
        action.addClassName("approval-cell");
        action.getElement().setAttribute("role", "cell");
        Div row = new Div(monthCell, statusCell,
                cell("cell", line.submittedAt() == null ? "" : dateTime(line.submittedAt(), locale),
                        getTranslation("employeeDetail.submitted")),
                cell("cell", line.decidedAt() == null ? "" : dateTime(line.decidedAt(), locale),
                        getTranslation("employeeDetail.decided")),
                cell("cell", line.status() == TimesheetStatus.REJECTED && line.rejectionReason() != null
                        ? line.rejectionReason() : "", getTranslation("employeeDetail.note")),
                action);
        row.addClassNames("approval-row", "detail-row");
        row.setTestId("timesheet-row");
        row.getElement().setAttribute("role", "row");
        return row;
    }

    private Div field(String labelKey, String value) {
        Span label = new Span(getTranslation(labelKey));
        label.addClassName("audit-detail-label");
        Span text = new Span(value == null ? "" : value);
        text.addClassName("detail-value");
        Div field = new Div(label, text);
        field.addClassName("detail-field");
        return field;
    }

    private static Span cell(String role, String text, String label) {
        Span cell = new Span(text == null ? "" : text);
        cell.addClassName("approval-cell");
        cell.getElement().setAttribute("role", role);
        if (label != null) {
            cell.getElement().setAttribute("data-label", label);
        }
        return cell;
    }

    private String dateTime(Instant at, Locale locale) {
        return at == null ? "" : DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
                .withZone(zone).format(at);
    }
}
