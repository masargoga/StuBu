package com.stubu.specdriven.monthlytimesheet;

import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.DurationFormat;
import com.stubu.specdriven.timetracking.EntryCorrectionDialogs;
import com.stubu.specdriven.timetracking.TimeEntry;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.stubu.specdriven.timetracking.TimeFormats;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * The employee's timesheet for a calendar month (UC-005): every day with its work periods, daily and monthly
 * totals, weekends and public holidays, and the status of the timesheet. The month is chosen with the arrows or
 * the month selector; only the current and earlier months can be shown. Work periods can be corrected while the
 * timesheet is a draft. Submitting is not available yet (UC-006), so the submit buttons are disabled.
 */
@Route(value = MonthlyTimesheetView.ROUTE, layout = MainLayout.class)
@PermitAll
public class MonthlyTimesheetView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle,
        LocaleChangeObserver {

    public static final String ROUTE = "timesheet";
    static final String MONTH_PARAMETER = "month";
    private static final Logger log = LoggerFactory.getLogger(MonthlyTimesheetView.class);
    private static final int SELECTABLE_MONTHS = 24;

    private enum ViewMode {
        TIMELINE, TABLE
    }

    /** The last message about an edit or delete, kept as key so it can be translated again. */
    private record Message(String key, boolean error) {
    }

    private final transient MonthlyTimesheetService service;
    private final transient TimeEntryService entryService;
    private final Long employeeId;
    private ZoneId zone;
    private YearMonth month;
    private transient MonthlyTimesheet sheet;
    private boolean loadFailed;
    private Message message;
    private ViewMode mode = ViewMode.TIMELINE;
    private boolean updatingSelector;

    private final H2 heading = new H2();
    private final Button previous = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final Button next = new Button(VaadinIcon.ANGLE_RIGHT.create());
    private final Select<YearMonth> monthSelect = new Select<>();
    private final Div statusBox = new Div();
    private final Badge statusBadge = new Badge();
    private final Span statusText = new Span();
    private final Button submit = new Button();
    private final Span submitNote = new Span();
    private final Div messageBox = new Div();
    private final Div loadErrorBox = new Div();
    private final Span loadError = new Span();
    private final Button retry = new Button();
    private final VerticalLayout content = new VerticalLayout();
    private final Span total = new Span();
    private final Span breaks = new Span();
    private final Div emptyHint = new Div();
    private final RadioButtonGroup<ViewMode> viewMode = new RadioButtonGroup<>();
    private final MonthTimeline timeline = new MonthTimeline();
    private final Grid<DayLine> table = new Grid<>();

    public MonthlyTimesheetView(AuthenticationContext authenticationContext, MonthlyTimesheetService service,
            TimeEntryService entryService) {
        this.service = service;
        this.entryService = entryService;
        this.zone = entryService.defaultZone();
        this.month = service.currentMonth(zone);
        this.employeeId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);

        addClassName("timesheet-view");
        setPadding(true);

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

        statusBadge.setTestId("timesheet-status");
        statusText.setTestId("status-text");
        submit.setTestId("submit-timesheet");
        submit.setEnabled(false); // UC-006
        submit.addThemeVariants(ButtonVariant.PRIMARY);
        submitNote.addClassName("status-note");
        Div statusLine = new Div(statusBadge, statusText);
        statusLine.addClassName("status-line");
        Div actions = new Div(submit, submitNote);
        actions.addClassName("status-actions");
        statusBox.add(statusLine, actions);
        statusBox.addClassName("timesheet-status");

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

        total.addClassNames("time-total", "month-total");
        total.setTestId("month-total");
        breaks.addClassName("time-total");
        breaks.setTestId("month-break");
        Div totals = new Div(total, breaks);
        totals.addClassName("time-totals");
        emptyHint.addClassName("time-empty");
        emptyHint.setTestId("empty-month");

        viewMode.setItems(ViewMode.values());
        viewMode.setValue(mode);
        viewMode.addClassName("view-mode");
        viewMode.setTestId("view-mode");
        viewMode.addValueChangeListener(event -> {
            mode = event.getValue() == null ? ViewMode.TIMELINE : event.getValue();
            render();
        });
        timeline.setEntryActions(this::edit, this::delete);
        table.addThemeVariants(GridVariant.NO_BORDER);
        table.setAllRowsVisible(true);
        table.setTestId("month-table");

        content.setPadding(false);
        content.setSpacing(true);
        content.add(statusBox, totals, emptyHint, viewMode, timeline, table);
        add(heading, navigation, messageBox, loadErrorBox, content);
        load();
    }

    // --- navigation and loading --------------------------------------------------------------------

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        YearMonth requested = event.getLocation().getQueryParameters().getSingleParameter(MONTH_PARAMETER)
                .map(MonthlyTimesheetView::parseMonth).orElse(null);
        YearMonth latest = service.currentMonth(zone);
        // Future months are never shown: fall back to the current month.
        YearMonth wanted = requested == null || requested.isAfter(latest) ? latest : requested;
        if (!wanted.equals(month) || sheet == null) {
            month = wanted;
            load();
            if (isAttached()) {
                render();
            }
        }
    }

    private static YearMonth parseMonth(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeException notAMonth) {
            return null;
        }
    }

    /** Shows another month by navigating, so the month is part of the address and survives a reload. */
    private void show(YearMonth target) {
        if (target.isAfter(service.currentMonth(zone))) {
            return;
        }
        getUI().ifPresent(ui -> ui.navigate(ROUTE, QueryParameters.of(MONTH_PARAMETER, target.toString())));
    }

    /** The browser's time zone decides what "today" and the current month are. */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        attachEvent.getUI().getPage().retrieveExtendedClientDetails(details -> {
            try {
                ZoneId browserZone = ZoneId.of(details.getTimeZoneId());
                if (!browserZone.equals(zone)) {
                    zone = browserZone;
                    if (month.isAfter(service.currentMonth(zone))) {
                        month = service.currentMonth(zone);
                    }
                    load();
                    render();
                }
            } catch (DateTimeException | NullPointerException unknownZone) {
                log.debug("Keeping the server time zone, the browser reported {}", details.getTimeZoneId());
            }
        });
    }

    /** Reloads the month from the server and redraws the page. */
    public void refresh() {
        load();
        render();
    }

    private void load() {
        if (employeeId == null) {
            return;
        }
        try {
            sheet = service.load(employeeId, month, zone);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load the timesheet {} of employee {}", month, employeeId, e);
            loadFailed = true;
        }
    }

    // --- editing -----------------------------------------------------------------------------------

    private Optional<TimeEntry> entryById(long entryId) {
        return sheet == null ? Optional.empty() : sheet.days().stream()
                .flatMap(line -> line.day().entries().stream()).filter(entry -> entry.getId() == entryId).findFirst();
    }

    private void edit(Long entryId) {
        entryById(entryId).ifPresentOrElse(entry -> new EntryCorrectionDialogs(entryService, employeeId)
                .openEdit(this, entry, zone, this::corrected), this::refresh);
    }

    private void delete(Long entryId) {
        entryById(entryId).ifPresentOrElse(entry -> new EntryCorrectionDialogs(entryService, employeeId)
                .openDelete(this, entry, zone, this::corrected), this::refresh);
    }

    private void corrected(EntryCorrectionDialogs.Outcome outcome) {
        message = switch (outcome) {
            case UPDATED -> new Message("time.edit.done", false);
            case DELETED -> new Message("time.delete.done", false);
            case LOCKED -> new Message("timesheet.locked", true);
            case GONE -> new Message("time.entry.gone", true);
        };
        refresh();
    }

    // --- rendering ---------------------------------------------------------------------------------

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    private void render() {
        Locale locale = getLocale();
        YearMonth latest = service.currentMonth(zone);

        heading.setText(getTranslation("timesheet.title"));
        previous.getElement().setAttribute("aria-label", getTranslation("timesheet.prev"));
        next.getElement().setAttribute("aria-label", getTranslation("timesheet.next"));
        next.setEnabled(month.isBefore(latest));
        monthSelect.setAriaLabel(getTranslation("timesheet.month"));
        updatingSelector = true;
        List<YearMonth> months = selectableMonths(latest);
        monthSelect.setItems(months);
        monthSelect.setItemLabelGenerator(ym -> monthLabel(ym, locale));
        monthSelect.setValue(month);
        updatingSelector = false;

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

        loadError.setText(getTranslation("timesheet.loadFailed"));
        retry.setText(getTranslation("time.retry"));
        loadErrorBox.setVisible(loadFailed);
        boolean showSheet = !loadFailed && sheet != null;
        content.setVisible(showSheet);
        if (!showSheet) {
            return;
        }

        renderStatus(locale);
        total.setText(getTranslation("timesheet.total", DurationFormat.format(sheet.totalWorked())));
        breaks.setText(getTranslation("time.break", DurationFormat.format(sheet.totalBreaks())));
        boolean empty = sheet.days().stream().allMatch(line -> line.day().entries().isEmpty());
        emptyHint.setText(getTranslation("timesheet.empty", monthLabel(month, locale)));
        emptyHint.setVisible(empty);

        viewMode.setLabel(getTranslation("timesheet.view.label"));
        viewMode.setItemLabelGenerator(item -> getTranslation("timesheet.view." + item.name().toLowerCase(Locale.ROOT)));
        viewMode.setValue(mode);
        timeline.setVisible(mode == ViewMode.TIMELINE);
        table.setVisible(mode == ViewMode.TABLE);
        Instant now = entryService.now();
        if (mode == ViewMode.TIMELINE) {
            timeline.show(sheet, zone, now, service.today(zone));
        } else {
            renderTable(locale, now);
        }
    }

    private void renderStatus(Locale locale) {
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
            case DRAFT -> getTranslation("timesheet.status.DRAFT");
            case SUBMITTED -> getTranslation("timesheet.status.SUBMITTED");
            case APPROVED -> sheet.approvedByName() == null
                    ? getTranslation("timesheet.status.APPROVED.noone", date(sheet.approvedAt(), locale))
                    : getTranslation("timesheet.status.APPROVED", date(sheet.approvedAt(), locale),
                            sheet.approvedByName());
            case REJECTED -> sheet.rejectionReason() == null
                    ? getTranslation("timesheet.status.REJECTED.noreason", date(sheet.rejectedAt(), locale))
                    : getTranslation("timesheet.status.REJECTED", date(sheet.rejectedAt(), locale),
                            sheet.rejectionReason());
        });
        // Submitting and resubmitting belong to UC-006 and UC-008: the buttons show what will be possible.
        boolean offersSubmit = status == TimesheetStatus.DRAFT || status == TimesheetStatus.REJECTED;
        submit.setText(getTranslation(status == TimesheetStatus.REJECTED ? "timesheet.resubmit" : "timesheet.submit"));
        submit.setVisible(offersSubmit);
        submitNote.setText(offersSubmit ? getTranslation("timesheet.submit.unavailable") : "");
        submitNote.setVisible(offersSubmit);
        if (!sheet.editable()) {
            submitNote.setVisible(true);
            submitNote.setText(getTranslation("timesheet.lockedNote"));
        }
    }

    private String date(Instant instant, Locale locale) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone)
                .format(instant);
    }

    private void renderTable(Locale locale, Instant now) {
        table.removeAllColumns();
        // The date cell carries the weekend/holiday note on a second line and the work periods wrap, so that
        // the essential columns still fit a phone screen.
        table.addColumn(line -> dateAndNote(line, locale)).setHeader(getTranslation("timesheet.table.date"))
                .setWidth("7.25rem").setFlexGrow(0);
        table.addColumn(line -> line.day().entries().stream().map(entry -> period(entry, line.date(), locale, now))
                .collect(Collectors.joining("  ·  "))).setHeader(getTranslation("timesheet.table.periods"))
                .setWidth("8rem").setFlexGrow(2);
        table.addColumn(line -> line.day().entries().isEmpty() ? "" : DurationFormat.format(line.day().worked()))
                .setHeader(getTranslation("timesheet.table.total")).setAutoWidth(true).setFlexGrow(0);
        table.addColumn(line -> line.day().entries().isEmpty() ? "" : DurationFormat.format(line.day().breaks()))
                .setHeader(getTranslation("timesheet.table.break")).setAutoWidth(true).setFlexGrow(0);
        table.setPartNameGenerator(line -> line.isHoliday() ? "holiday-cell" : line.weekend() ? "weekend-cell" : null);
        table.setItems(new ArrayList<>(sheet.days()));
    }

    private String dateAndNote(DayLine line, Locale locale) {
        String date = line.date().format(DateTimeFormatter.ofPattern("EEE, d MMM", locale));
        String note = note(line);
        return note.isEmpty() ? date : date + "\n" + note;
    }

    private String note(DayLine line) {
        List<String> notes = new ArrayList<>();
        if (line.weekend()) {
            notes.add(getTranslation("timesheet.weekend"));
        }
        if (line.isHoliday()) {
            notes.add(getTranslation("timesheet.holiday", line.holidayName()));
        }
        return String.join(", ", notes);
    }

    private String period(TimeEntry entry, LocalDate date, Locale locale, Instant now) {
        String start = TimeFormats.timeOrDateTime(entry.getCheckInAt(), zone, locale, date);
        return entry.isActive() ? start + " – " + getTranslation("time.status.inProgress")
                : start + " – " + TimeFormats.timeOrDateTime(entry.getCheckOutAt(), zone, locale, date);
    }

    /** The current month and the previous ones, and always the month being shown. */
    private List<YearMonth> selectableMonths(YearMonth latest) {
        List<YearMonth> months = new ArrayList<>();
        for (int back = 0; back < SELECTABLE_MONTHS; back++) {
            months.add(latest.minusMonths(back));
        }
        if (!months.contains(month)) {
            months.add(month);
            months.sort(Comparator.reverseOrder());
        }
        return months;
    }

    private static String monthLabel(YearMonth period, Locale locale) {
        return period.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("timesheet.title");
    }
}
