package com.stubu.specdriven.admin;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * The audit log for administrators (UC-013): the newest entries first, page by page, with filters for the date range,
 * the user, the entity type and the action, the details of an entry on request, and an export of the matching
 * entries as CSV. It only shows; nothing here can change or delete an entry.
 */
@Route(value = AuditLogView.ROUTE, layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AuditLogView extends VerticalLayout implements HasDynamicTitle, LocaleChangeObserver {

    public static final String ROUTE = "admin/audit";
    private static final Logger log = LoggerFactory.getLogger(AuditLogView.class);

    private final transient AuditLogService service;
    private final transient TimeEntryService entryService;
    private final Long adminId;
    private ZoneId zone;
    private AuditLogService.Filter applied = AuditLogService.Filter.NONE;
    private int page;
    private transient AuditLogService.Result result;
    private transient List<String> entityTypes = List.of();
    private boolean anyEntries = true;
    private boolean loadFailed;
    private boolean updatingFilters;

    private final H2 heading = new H2();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final TextField user = new TextField();
    private final Select<String> entityType = new Select<>();
    private final Select<AuditAction> action = new Select<>();
    private final Button search = new Button(VaadinIcon.SEARCH.create());
    private final Button reset = new Button();
    private final Anchor export = new Anchor();
    private final Div filters = new Div();
    private final Div loadErrorBox = new Div();
    private final Span loadError = new Span();
    private final Button retry = new Button();
    private final Div emptyHint = new Div();
    private final Span summary = new Span();
    private final Div list = new Div();
    private final Button previous = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final Button next = new Button(VaadinIcon.ANGLE_RIGHT.create());
    private final Span pageInfo = new Span();
    private final Div pager = new Div();

    public AuditLogView(AuthenticationContext authenticationContext, AuditLogService service,
            TimeEntryService entryService) {
        this.service = service;
        this.entryService = entryService;
        this.zone = entryService.defaultZone();
        this.adminId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);

        addClassNames("timesheet-view", "manage-view", "audit-view");
        setPadding(true);

        from.setTestId("audit-from");
        to.setTestId("audit-to");
        user.setTestId("audit-user");
        user.setClearButtonVisible(true);
        user.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, event -> applyFilters());
        entityType.setTestId("audit-entity-type");
        entityType.setEmptySelectionAllowed(true);
        entityType.setItemLabelGenerator(value -> value == null ? getTranslation("audit.filter.all") : value);
        action.setTestId("audit-action");
        action.setEmptySelectionAllowed(true);
        action.setItems(AuditAction.values());
        action.setItemLabelGenerator(value -> value == null ? getTranslation("audit.filter.all")
                : getTranslation("audit.action." + value.name()));
        search.setTestId("audit-search");
        search.addThemeVariants(ButtonVariant.PRIMARY);
        search.addClickListener(event -> applyFilters());
        reset.setTestId("audit-reset");
        reset.addThemeVariants(ButtonVariant.TERTIARY);
        reset.addClickListener(event -> resetFilters());
        export.setTestId("audit-export");
        export.addClassNames("export-link");
        export.getElement().setAttribute("download", true);
        Div buttons = new Div(search, reset, export);
        buttons.addClassName("audit-buttons");
        filters.add(from, to, user, entityType, action, buttons);
        filters.addClassName("audit-filters");

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
        emptyHint.setTestId("no-entries");
        summary.setTestId("audit-summary");
        summary.addClassName("audit-summary");
        list.addClassNames("approvals-list", "audit-list");
        list.setTestId("audit-list");
        list.getElement().setAttribute("role", "table");
        previous.setTestId("audit-previous");
        previous.addThemeVariants(ButtonVariant.TERTIARY);
        previous.addClickListener(event -> goTo(page - 1));
        next.setTestId("audit-next");
        next.addThemeVariants(ButtonVariant.TERTIARY);
        next.addClickListener(event -> goTo(page + 1));
        pageInfo.setTestId("audit-page");
        pager.add(previous, pageInfo, next);
        pager.addClassName("audit-pager");

        add(heading, filters, loadErrorBox, emptyHint, summary, list, pager);
        load();
    }

    /** The browser's time zone decides how times are shown and what a day is in the date filter. */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        attachEvent.getUI().getPage().retrieveExtendedClientDetails(details -> {
            try {
                ZoneId browserZone = ZoneId.of(details.getTimeZoneId());
                if (!browserZone.equals(zone)) {
                    zone = browserZone;
                    refresh();
                }
            } catch (DateTimeException | NullPointerException unknownZone) {
                log.debug("Keeping the server time zone, the browser reported {}", details.getTimeZoneId());
            }
        });
    }

    // --- searching ---------------------------------------------------------------------------------

    private void applyFilters() {
        applied = new AuditLogService.Filter(from.getValue(), to.getValue(), user.getValue(),
                entityType.getValue(), action.getValue());
        page = 0;
        refresh();
    }

    private void resetFilters() {
        updatingFilters = true;
        from.clear();
        to.clear();
        user.clear();
        entityType.clear();
        action.clear();
        updatingFilters = false;
        applyFilters();
    }

    private void goTo(int target) {
        page = Math.max(target, 0);
        refresh();
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
            anyEntries = service.hasEntries(adminId);
            entityTypes = service.entityTypes(adminId);
            result = service.search(adminId, applied, zone, page);
            if (page >= result.pages()) { // the log shrank or the filter narrowed: show the last page
                page = result.pages() - 1;
                result = service.search(adminId, applied, zone, page);
            }
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load the audit log for administrator {}", adminId, e);
            loadFailed = true;
        }
        export.setHref(new StreamResource("audit-log.csv", () -> new ByteArrayInputStream(
                service.exportCsv(adminId, applied, zone).getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("audit.title");
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    // --- rendering ---------------------------------------------------------------------------------

    private void render() {
        Locale locale = getLocale();
        heading.setText(getTranslation("audit.title"));
        from.setLabel(getTranslation("audit.filter.from"));
        from.setLocale(locale);
        to.setLabel(getTranslation("audit.filter.to"));
        to.setLocale(locale);
        user.setLabel(getTranslation("audit.filter.user"));
        user.setPlaceholder(getTranslation("audit.filter.user.placeholder"));
        entityType.setLabel(getTranslation("audit.filter.entityType"));
        entityType.setEmptySelectionCaption(getTranslation("audit.filter.all"));
        String chosenType = entityType.getValue();
        updatingFilters = true;
        entityType.setItems(entityTypes);
        entityType.setValue(chosenType != null && entityTypes.contains(chosenType) ? chosenType : null);
        updatingFilters = false;
        action.setLabel(getTranslation("audit.filter.action"));
        action.setEmptySelectionCaption(getTranslation("audit.filter.all"));
        search.setText(getTranslation("audit.search"));
        reset.setText(getTranslation("audit.reset"));
        export.setText(getTranslation("audit.export"));

        loadError.setText(getTranslation("audit.loadFailed"));
        retry.setText(getTranslation("time.retry"));
        loadErrorBox.setVisible(loadFailed);
        filters.setVisible(!loadFailed);
        boolean hasRows = !loadFailed && result != null && !result.rows().isEmpty();
        emptyHint.setText(getTranslation(anyEntries ? "audit.noMatch" : "audit.none"));
        emptyHint.setVisible(!loadFailed && !hasRows);
        summary.setVisible(hasRows);
        list.setVisible(hasRows);
        pager.setVisible(hasRows && result.pages() > 1);
        export.setVisible(hasRows);
        if (!hasRows) {
            return;
        }

        summary.setText(getTranslation("audit.count", result.total()));
        pageInfo.setText(getTranslation("audit.page", result.page() + 1, result.pages()));
        previous.setEnabled(result.page() > 0);
        next.setEnabled(result.page() + 1 < result.pages());
        previous.getElement().setAttribute("aria-label", getTranslation("audit.previous"));
        next.getElement().setAttribute("aria-label", getTranslation("audit.next"));

        list.removeAll();
        list.getElement().setAttribute("aria-label", getTranslation("audit.title"));
        Div head = new Div(cell("columnheader", getTranslation("audit.time"), null),
                cell("columnheader", getTranslation("audit.user"), null),
                cell("columnheader", getTranslation("audit.entity"), null),
                cell("columnheader", getTranslation("audit.action"), null),
                cell("columnheader", getTranslation("audit.reason"), null),
                cell("columnheader", getTranslation("audit.changes"), null),
                cell("columnheader", getTranslation("audit.details"), null));
        head.addClassName("approval-head");
        head.getElement().setAttribute("role", "row");
        list.add(head);
        DateTimeFormatter time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
                .withZone(zone);
        for (AuditLogService.Row row : result.rows()) {
            list.add(row(row, time));
        }
    }

    private Div row(AuditLogService.Row entry, DateTimeFormatter time) {
        Span who = cell("cell", entry.isSystem() ? getTranslation("audit.system") : entry.userName() == null
                ? "#" + entry.userId() : entry.userName(), getTranslation("audit.user"));
        Span what = cell("cell", entry.entityType() + (entry.entityId() == null ? "" : " #" + entry.entityId()),
                getTranslation("audit.entity"));
        Button details = new Button(getTranslation("audit.details"));
        details.setTestId("audit-details");
        details.addThemeVariants(ButtonVariant.TERTIARY);
        details.addClassName("review-button");
        details.getElement().setAttribute("aria-label", getTranslation("audit.details.label", entry.id()));
        details.addClickListener(event -> openDetails(entry, time));
        Div detailsCell = new Div(details);
        detailsCell.addClassName("approval-cell");
        detailsCell.getElement().setAttribute("role", "cell");
        Span reason = cell("cell", entry.reason() == null ? "" : entry.reason(), getTranslation("audit.reason"));
        Span changes = cell("cell", entry.summary(), getTranslation("audit.changes"));
        changes.addClassName("audit-changes");
        Div row = new Div(cell("cell", time.format(entry.timestamp()), getTranslation("audit.time")), who, what,
                cell("cell", getTranslation("audit.action." + entry.action().name()), getTranslation("audit.action")),
                reason, changes, detailsCell);
        row.addClassNames("approval-row", "audit-row");
        row.setTestId("audit-row");
        row.getElement().setAttribute("role", "row");
        return row;
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

    // --- one entry ---------------------------------------------------------------------------------

    private void openDetails(AuditLogService.Row entry, DateTimeFormatter time) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(44rem, 94vw)");
        dialog.setHeaderTitle(getTranslation("audit.details.title", entry.id()));
        String who = entry.isSystem() ? getTranslation("audit.system") : (entry.userName() == null
                ? "#" + entry.userId() : entry.userName() + " (" + entry.userEmail() + ", #" + entry.userId() + ")");
        Div body = new Div(field("audit.time", time.format(entry.timestamp()) + " (" + zone.getId() + ")"),
                field("audit.user", who),
                field("audit.entity", entry.entityType() + (entry.entityId() == null ? "" : " #" + entry.entityId())),
                field("audit.action", getTranslation("audit.action." + entry.action().name())),
                field("audit.reason", entry.reason() == null ? "–" : entry.reason()),
                field("audit.old", AuditSummary.lines(entry.oldValues()).isEmpty() ? "–" : AuditSummary.lines(
                        entry.oldValues())),
                field("audit.new", AuditSummary.lines(entry.newValues()).isEmpty() ? "–" : AuditSummary.lines(
                        entry.newValues())));
        body.addClassNames("time-dialog-content", "audit-detail");
        dialog.add(body);
        Button close = new Button(getTranslation("audit.close"), event -> dialog.close());
        close.addThemeVariants(ButtonVariant.PRIMARY);
        close.setTestId("audit-close");
        close.addClassName("time-dialog-button");
        dialog.getFooter().add(close);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private Div field(String labelKey, String value) {
        Span label = new Span(getTranslation(labelKey));
        label.addClassName("audit-detail-label");
        Pre text = new Pre(value);
        text.addClassName("audit-detail-value");
        Div field = new Div(label, text);
        field.addClassName("audit-detail-field");
        return field;
    }
}
