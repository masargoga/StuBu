package com.stubu.specdriven.approval;

import com.stubu.specdriven.base.FlashMessage;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.YearMonth;
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
 * The manager's list of submitted timesheets that wait for a decision (UC-007), oldest first. One timesheet is
 * reviewed at a time; there is no bulk approval.
 */
@Route(value = ApprovalsView.ROUTE, layout = MainLayout.class)
@RolesAllowed({ "MANAGER", "ADMIN" })
public class ApprovalsView extends VerticalLayout implements HasDynamicTitle, LocaleChangeObserver {

    public static final String ROUTE = "approvals";
    private static final Logger log = LoggerFactory.getLogger(ApprovalsView.class);

    private final transient TimesheetReviewService service;
    private final Long reviewerId;
    private ZoneId zone;
    private transient List<PendingApproval> pending = List.of();
    private boolean loadFailed;
    private ReviewScope scope = ReviewScopePreference.get();
    private transient ScopeOptions options;
    private FlashMessage message;

    private final H2 heading = new H2();
    private final ScopeSwitcher scopeSwitcher = new ScopeSwitcher();
    private final Div messageBox = new Div();
    private final Div loadErrorBox = new Div();
    private final Span loadError = new Span();
    private final Button retry = new Button();
    private final Div emptyHint = new Div();
    private final Div list = new Div();

    public ApprovalsView(AuthenticationContext authenticationContext, TimesheetReviewService service,
            TimeEntryService entryService) {
        this.service = service;
        this.zone = entryService.defaultZone();
        this.reviewerId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);
        message = FlashMessage.take();

        addClassNames("timesheet-view", "approvals-view");
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

        emptyHint.addClassName("time-empty");
        emptyHint.setTestId("no-approvals");
        list.addClassName("approvals-list");
        list.setTestId("approvals");
        list.getElement().setAttribute("role", "table");

        add(heading, messageBox, loadErrorBox, scopeSwitcher, emptyHint, list);
        scopeSwitcher.addScopeListener(this::switchScope);
        load();
    }

    /** The browser's time zone decides how the submission dates are shown. */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        attachEvent.getUI().getPage().retrieveExtendedClientDetails(details -> {
            try {
                ZoneId browserZone = ZoneId.of(details.getTimeZoneId());
                if (!browserZone.equals(zone)) {
                    zone = browserZone;
                    render();
                }
            } catch (DateTimeException | NullPointerException unknownZone) {
                log.debug("Keeping the server time zone, the browser reported {}", details.getTimeZoneId());
            }
        });
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
            pending = service.pending(reviewerId, scope);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load the pending approvals of reviewer {}", reviewerId, e);
            loadFailed = true;
        }
    }

    /** The manager picked another scope: show it, or keep the previous one if it cannot be loaded (AF-3). */
    private void switchScope(ReviewScope newScope) {
        try {
            pending = service.pending(reviewerId, newScope);
            scope = newScope;
            ReviewScopePreference.set(scope);
            message = null;
        } catch (DataAccessException e) {
            log.error("Could not load the pending approvals of reviewer {} for {}", reviewerId, newScope, e);
            message = new FlashMessage("scope.loadFailed", true);
        }
        render();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("approvals.title");
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    private void render() {
        Locale locale = getLocale();
        heading.setText(getTranslation("approvals.title"));

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

        scopeSwitcher.setVisible(!loadFailed && options != null);
        if (options != null) {
            scopeSwitcher.update(options, scope);
        }
        loadError.setText(getTranslation("approvals.loadFailed"));
        retry.setText(getTranslation("time.retry"));
        loadErrorBox.setVisible(loadFailed);
        boolean showList = !loadFailed;
        emptyHint.setText(getTranslation("approvals.empty"));
        emptyHint.setVisible(showList && pending.isEmpty());
        list.setVisible(showList && !pending.isEmpty());
        if (!list.isVisible()) {
            return;
        }

        list.removeAll();
        list.getElement().setAttribute("aria-label", getTranslation("approvals.title"));
        Div head = new Div(cell("columnheader", getTranslation("approvals.employee"), null),
                cell("columnheader", getTranslation("approvals.period"), null),
                cell("columnheader", getTranslation("approvals.submitted"), null),
                cell("columnheader", getTranslation("approvals.action"), null));
        head.addClassName("approval-head");
        head.getElement().setAttribute("role", "row");
        list.add(head);
        for (PendingApproval approval : pending) {
            Span employee = cell("cell", approval.employeeName(), null);
            employee.addClassName("approval-employee");
            Div action = new Div(reviewButton(approval, locale));
            action.addClassName("approval-cell");
            action.getElement().setAttribute("role", "cell");
            Div row = new Div(employee,
                    cell("cell", month(approval.period(), locale), getTranslation("approvals.period")),
                    cell("cell", submitted(approval.submittedAt(), locale), getTranslation("approvals.submitted")),
                    action);
            row.addClassName("approval-row");
            row.setTestId("approval-row");
            row.getElement().setAttribute("role", "row");
            list.add(row);
        }
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

    private Button reviewButton(PendingApproval approval, Locale locale) {
        Button review = new Button(getTranslation("approvals.review"));
        review.setTestId("review");
        review.addThemeVariants(ButtonVariant.PRIMARY);
        review.addClassName("review-button");
        review.getElement().setAttribute("aria-label", getTranslation("approvals.review.label",
                approval.employeeName(), month(approval.period(), locale)));
        review.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(TimesheetReviewView.class,
                approval.timesheetId())));
        return review;
    }

    private String submitted(Instant submittedAt, Locale locale) {
        return submittedAt == null ? "" : DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                .withZone(zone).format(submittedAt);
    }

    static String month(YearMonth period, Locale locale) {
        return period.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + period.getYear();
    }
}
