package com.stubu.specdriven.approval;

import com.stubu.specdriven.base.BrowserTimeZone;
import com.stubu.specdriven.base.FlashMessage;
import com.stubu.specdriven.base.LoadErrorBox;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.monthlytimesheet.MonthTimeline;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheet;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.DurationFormat;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.stubu.specdriven.timetracking.WorkTimeline;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * A manager looks at the timesheet of one employee for a month (UC-009): every day with its work periods, the
 * totals, the status, the history of submission and decisions, and the timeline of a single day on request. The
 * page is read-only; a submitted timesheet is decided on the review page (UC-007).
 */
@Route(value = "employees/timesheet", layout = MainLayout.class)
@RolesAllowed({ "MANAGER", "ADMIN" })
public class EmployeeTimesheetView extends VerticalLayout implements HasUrlParameter<Long>, HasDynamicTitle,
        LocaleChangeObserver {

    static final String MONTH_PARAMETER = "month";
    private static final int SELECTABLE_MONTHS = 24;
    private static final Logger log = LoggerFactory.getLogger(EmployeeTimesheetView.class);

    private final transient TimesheetReviewService service;
    private final transient TimeEntryService entryService;
    private final Long reviewerId;
    private ZoneId zone;
    private Long employeeId;
    private YearMonth month;
    private transient EmployeeTimesheetDetails details;
    private boolean loadFailed;
    private boolean updatingSelector;

    private final Button back = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final H2 heading = new H2();
    private final Badge roleBadge = new Badge();
    private final Button previous = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final Button next = new Button(VaadinIcon.ANGLE_RIGHT.create());
    private final Select<YearMonth> monthSelect = new Select<>();
    private final LoadErrorBox loadErrorBox = new LoadErrorBox(this::refresh);
    private final Div noTimesheet = new Div();
    private final VerticalLayout content = new VerticalLayout();
    private final Div statusBox = new Div();
    private final Badge statusBadge = new Badge();
    private final Span statusText = new Span();
    private final Button decide = new Button();
    private final Span total = new Span();
    private final Span breaks = new Span();
    private final Div emptyHint = new Div();
    private final MonthTimeline timeline = new MonthTimeline();
    private final TimesheetHistory history = new TimesheetHistory();

    public EmployeeTimesheetView(AuthenticationContext authenticationContext, TimesheetReviewService service,
            TimeEntryService entryService) {
        this.service = service;
        this.entryService = entryService;
        this.zone = entryService.defaultZone();
        this.month = currentMonth();
        this.reviewerId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);

        addClassNames("timesheet-view", "employee-timesheet-view");
        setPadding(true);

        back.setTestId("back");
        back.addThemeVariants(ButtonVariant.TERTIARY);
        // Administrators come from an employee's details, managers from their list of employees.
        boolean administrator = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getRole).filter(role -> role == com.stubu.specdriven.employee.Role.ADMIN).isPresent();
        back.addClickListener(event -> getUI().ifPresent(ui -> {
            if (administrator) {
                ui.getPage().getHistory().back();
            } else {
                ui.navigate(EmployeesView.class);
            }
        }));
        roleBadge.setTestId("role-badge");
        HorizontalLayout title = new HorizontalLayout(heading, roleBadge);
        title.setAlignItems(FlexComponent.Alignment.CENTER);
        title.setPadding(false);
        title.setWrap(true);

        previous.setTestId("previous-month");
        previous.addThemeVariants(ButtonVariant.TERTIARY);
        previous.addClickListener(event -> show(month.minusMonths(1)));
        next.setTestId("next-month");
        next.addThemeVariants(ButtonVariant.TERTIARY);
        next.addClickListener(event -> show(month.plusMonths(1)));
        monthSelect.setTestId("month-select");
        monthSelect.addValueChangeListener(event -> {
            if (!updatingSelector && event.getValue() != null && !event.getValue().equals(month)) {
                show(event.getValue());
            }
        });
        HorizontalLayout navigation = new HorizontalLayout(previous, monthSelect, next);
        navigation.addClassName("month-navigation");
        navigation.setAlignItems(FlexComponent.Alignment.CENTER);

        noTimesheet.addClassName("time-empty");
        noTimesheet.setTestId("no-timesheet");

        statusBadge.setTestId("timesheet-status");
        statusText.setTestId("status-text");
        decide.setTestId("decide");
        decide.addThemeVariants(ButtonVariant.PRIMARY);
        decide.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(TimesheetReviewView.class,
                details.timesheetId())));
        Div statusLine = new Div(statusBadge, statusText);
        statusLine.addClassName("status-line");
        Div actions = new Div(decide);
        actions.addClassName("status-actions");
        statusBox.add(statusLine, actions);
        statusBox.addClassName("timesheet-status");

        total.addClassNames("time-total", "month-total");
        total.setTestId("month-total");
        breaks.addClassName("time-total");
        breaks.setTestId("month-break");
        Div totals = new Div(total, breaks);
        totals.addClassName("time-totals");
        emptyHint.addClassName("time-empty");
        emptyHint.setTestId("empty-month");
        timeline.setReadOnly(true);
        timeline.setDayDetails(this::openDay);

        content.setPadding(false);
        content.setSpacing(true);
        content.add(statusBox, totals, emptyHint, timeline, history);
        add(back, title, navigation, loadErrorBox, noTimesheet, content);
    }

    // --- navigation and loading --------------------------------------------------------------------

    @Override
    public void setParameter(BeforeEvent event, Long employeeId) {
        this.employeeId = employeeId;
        YearMonth latest = currentMonth();
        YearMonth requested = event.getLocation().getQueryParameters().getSingleParameter(MONTH_PARAMETER)
                .map(EmployeeTimesheetView::parseMonth).orElse(null);
        month = requested == null || requested.isAfter(latest) ? latest : requested;
        try {
            load();
        } catch (ReviewNotAllowedException notAllowed) {
            // Also for an employee who does not exist: the two are not told apart.
            FlashMessage.set("employees.noPermission", true);
            event.forwardTo(EmployeesView.class);
            return;
        }
        if (isAttached()) {
            render();
        }
    }

    private static YearMonth parseMonth(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeException notAMonth) {
            return null;
        }
    }

    private YearMonth currentMonth() {
        return YearMonth.from(entryService.currentDate(zone));
    }

    /** Shows another month by navigating, so the month is part of the address and survives a reload. */
    private void show(YearMonth target) {
        if (target.isAfter(currentMonth())) {
            return;
        }
        getUI().ifPresent(ui -> ui.navigate(EmployeeTimesheetView.class, employeeId,
                QueryParameters.of(MONTH_PARAMETER, target.toString())));
    }

    /** The browser's time zone decides which day an entry belongs to. */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        BrowserTimeZone.detect(attachEvent.getUI(), zone, browserZone -> {
            zone = browserZone;
            if (month.isAfter(currentMonth())) {
                month = currentMonth();
            }
            refresh();
        });
    }

    private void refresh() {
        try {
            load();
        } catch (ReviewNotAllowedException notAllowed) {
            FlashMessage.set("employees.noPermission", true);
            getUI().ifPresent(ui -> ui.navigate(EmployeesView.class));
            return;
        }
        render();
    }

    private void load() {
        if (reviewerId == null || employeeId == null) {
            throw new ReviewNotAllowedException();
        }
        try {
            details = service.employeeTimesheet(reviewerId, employeeId, month, zone);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load the timesheet {} of employee {} for {}", month, employeeId, reviewerId, e);
            loadFailed = true;
        }
    }

    @Override
    public String getPageTitle() {
        return getTranslation("employeeTimesheet.pageTitle");
    }

    // --- rendering ---------------------------------------------------------------------------------

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    private void render() {
        Locale locale = getLocale();
        YearMonth latest = currentMonth();
        back.setText(getTranslation("employeeTimesheet.back"));
        back.getElement().setAttribute("aria-label", getTranslation("employeeTimesheet.back"));
        previous.getElement().setAttribute("aria-label", getTranslation("timesheet.prev"));
        next.getElement().setAttribute("aria-label", getTranslation("timesheet.next"));
        next.setEnabled(month.isBefore(latest));
        monthSelect.setAriaLabel(getTranslation("timesheet.month"));
        updatingSelector = true;
        monthSelect.setItems(selectableMonths(latest));
        monthSelect.setItemLabelGenerator(ym -> ApprovalsView.month(ym, locale));
        monthSelect.setValue(month);
        updatingSelector = false;

        loadErrorBox.update(loadFailed, getTranslation("timesheet.loadFailed"), getTranslation("time.retry"));
        boolean loaded = !loadFailed && details != null;
        heading.setVisible(loaded);
        roleBadge.setVisible(loaded);
        noTimesheet.setVisible(false);
        content.setVisible(false);
        if (!loaded) {
            return;
        }
        heading.setText(details.employeeName());
        roleBadge.setText(getTranslation("role." + details.role().name()));
        if (!details.exists()) {
            noTimesheet.setText(getTranslation("employeeTimesheet.none", details.employeeName(),
                    ApprovalsView.month(month, locale)));
            noTimesheet.setVisible(true);
            return;
        }
        content.setVisible(true);
        MonthlyTimesheet sheet = details.sheet();
        renderStatus(sheet, locale);
        total.setText(getTranslation("timesheet.total", DurationFormat.format(sheet.totalWorked())));
        breaks.setText(getTranslation("time.break", DurationFormat.format(sheet.totalBreaks())));
        boolean empty = sheet.days().stream().allMatch(line -> line.day().entries().isEmpty());
        emptyHint.setText(getTranslation("timesheet.empty", ApprovalsView.month(month, locale)));
        emptyHint.setVisible(empty);
        timeline.show(sheet, zone, entryService.now(), entryService.currentDate(zone));
        history.show(details.history(), zone);
    }

    private void renderStatus(MonthlyTimesheet sheet, Locale locale) {
        TimesheetStatus status = sheet.status();
        statusBadge.setText(getTranslation("timesheet.badge." + status.name()));
        statusBadge.getElement().setAttribute("theme", "");
        switch (status) {
            case DRAFT -> statusBadge.addThemeVariants(BadgeVariant.WARNING);
            case SUBMITTED -> statusBadge.addThemeVariants();
            case APPROVED -> statusBadge.addThemeVariants(BadgeVariant.SUCCESS);
            case REJECTED -> statusBadge.addThemeVariants(BadgeVariant.ERROR);
        }
        statusText.setText(switch (status) {
            case DRAFT -> getTranslation("review.status.DRAFT");
            case SUBMITTED -> sheet.submittedAt() == null ? getTranslation("timesheet.status.SUBMITTED.nodate")
                    : sheet.rejectedAt() == null
                            ? getTranslation("timesheet.status.SUBMITTED", date(sheet.submittedAt(), locale))
                            : getTranslation("timesheet.status.SUBMITTED.resubmitted",
                                    date(sheet.submittedAt(), locale), date(sheet.rejectedAt(), locale));
            case APPROVED -> sheet.approvedByName() == null
                    ? getTranslation("timesheet.status.APPROVED.noone", date(sheet.approvedAt(), locale))
                    : getTranslation("timesheet.status.APPROVED", date(sheet.approvedAt(), locale),
                            sheet.approvedByName());
            case REJECTED -> sheet.rejectionReason() == null
                    ? getTranslation("timesheet.status.REJECTED.noreason", date(sheet.rejectedAt(), locale))
                    : getTranslation("timesheet.status.REJECTED", date(sheet.rejectedAt(), locale),
                            sheet.rejectionReason());
        });
        decide.setText(getTranslation("employeeTimesheet.decide"));
        decide.setVisible(status == TimesheetStatus.SUBMITTED && details.canDecide());
    }

    private String date(Instant instant, Locale locale) {
        return instant == null ? "" : DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                .withZone(zone).format(instant);
    }

    private List<YearMonth> selectableMonths(YearMonth latest) {
        List<YearMonth> months = new ArrayList<>();
        for (int back = 0; back < SELECTABLE_MONTHS; back++) {
            months.add(latest.minusMonths(back));
        }
        if (!months.contains(month)) {
            months.add(month);
            months.sort(java.util.Comparator.reverseOrder());
        }
        return months;
    }

    // --- one day -----------------------------------------------------------------------------------

    /** The timeline of a single day, as the employee sees it on their own page, but read-only. */
    private void openDay(LocalDate date) {
        details.sheet().days().stream().filter(line -> line.date().equals(date)).findFirst().ifPresent(line -> {
            Locale locale = getLocale();
            Dialog dialog = new Dialog();
            dialog.setWidth("min(48rem, 94vw)");
            dialog.setHeaderTitle(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    .withLocale(locale)));
            WorkTimeline dayTimeline = new WorkTimeline();
            dayTimeline.setReadOnly(true);
            dayTimeline.show(line.day(), zone, entryService.now());
            Span totals = new Span(getTranslation("timesheet.day.summary", DurationFormat.format(line.day().worked()),
                    DurationFormat.format(line.day().breaks())));
            totals.addClassName("time-total");
            totals.setTestId("day-total");
            Div body = new Div(dayTimeline, totals);
            body.addClassNames("time-dialog-content", "review-dialog-content");
            dialog.add(body);
            Button close = new Button(getTranslation("employeeTimesheet.close"), event -> dialog.close());
            close.addThemeVariants(ButtonVariant.PRIMARY);
            close.setTestId("day-close");
            close.addClassName("time-dialog-button");
            dialog.getFooter().add(close);
            dialog.addClosedListener(event -> dialog.removeFromParent());
            dialog.open();
        });
    }
}
