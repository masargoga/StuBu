package com.stubu.specdriven.monthlytimesheet;

import com.stubu.specdriven.base.BrowserTimeZone;
import com.stubu.specdriven.base.DialogError;
import com.stubu.specdriven.base.LoadErrorBox;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.base.MessageBox;
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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
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
 * timesheet is a draft or was rejected. A finished month can be submitted for approval (UC-006), and a rejected
 * timesheet resubmitted once it is corrected (UC-008).
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
    private final transient TimesheetSubmissionService submissionService;
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
    private final Button addEntry = new Button(VaadinIcon.PLUS.create());
    private final Div statusBox = new Div();
    private final Badge statusBadge = new Badge();
    private final Span statusText = new Span();
    private final Button submit = new Button();
    private final Span submitNote = new Span();
    private final MessageBox messageBox = new MessageBox();
    private final LoadErrorBox loadErrorBox = new LoadErrorBox(this::refresh);
    private final VerticalLayout content = new VerticalLayout();
    private final Span total = new Span();
    private final Span breaks = new Span();
    private final Div emptyHint = new Div();
    private final RadioButtonGroup<ViewMode> viewMode = new RadioButtonGroup<>();
    private final MonthTimeline timeline = new MonthTimeline();
    private final RegionNote regionNote = new RegionNote();
    private final Grid<DayLine> table = new Grid<>();

    public MonthlyTimesheetView(AuthenticationContext authenticationContext, MonthlyTimesheetService service,
            TimeEntryService entryService, TimesheetSubmissionService submissionService) {
        this.service = service;
        this.entryService = entryService;
        this.submissionService = submissionService;
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
        addEntry.setTestId("add-time-entry");
        addEntry.addClassName("month-add");
        addEntry.setEnabled(false);
        addEntry.addClickListener(event -> openAdd(null));
        HorizontalLayout navigation = new HorizontalLayout(previous, monthSelect, next, addEntry);
        navigation.addClassName("month-navigation");
        navigation.setAlignItems(FlexComponent.Alignment.CENTER);

        statusBadge.setTestId("timesheet-status");
        statusText.setTestId("status-text");
        submit.setTestId("submit-timesheet");
        submit.addThemeVariants(ButtonVariant.PRIMARY);
        submit.addClickListener(event -> submitClicked());
        submitNote.addClassName("status-note");
        Div statusLine = new Div(statusBadge, statusText);
        statusLine.addClassName("status-line");
        Div actions = new Div(submit, submitNote);
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

        viewMode.setItems(ViewMode.values());
        viewMode.setValue(mode);
        viewMode.addClassName("view-mode");
        viewMode.setTestId("view-mode");
        viewMode.addValueChangeListener(event -> {
            mode = event.getValue() == null ? ViewMode.TIMELINE : event.getValue();
            render();
        });
        timeline.setEntryActions(this::edit, this::delete);
        timeline.setAddEntry(this::openAdd);
        table.addThemeVariants(GridVariant.NO_BORDER);
        table.setAllRowsVisible(true);
        table.setTestId("month-table");

        content.setPadding(false);
        content.setSpacing(true);
        content.add(statusBox, totals, emptyHint, viewMode, regionNote, timeline, table);
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
        BrowserTimeZone.detect(attachEvent.getUI(), zone, browserZone -> {
            zone = browserZone;
            if (month.isAfter(service.currentMonth(zone))) {
                month = service.currentMonth(zone);
            }
            load();
            render();
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

    /** Opens the form to add a work period afterwards; {@code date} is preset when started from a day row. */
    private void openAdd(LocalDate date) {
        new EntryCorrectionDialogs(entryService, employeeId).openAdd(this, date, zone, this::corrected);
    }

    private void delete(Long entryId) {
        entryById(entryId).ifPresentOrElse(entry -> new EntryCorrectionDialogs(entryService, employeeId)
                .openDelete(this, entry, zone, this::corrected), this::refresh);
    }

    private void corrected(EntryCorrectionDialogs.Outcome outcome) {
        message = switch (outcome) {
            case UPDATED -> new Message("time.edit.done", false);
            case ADDED -> new Message("time.add.done", false);
            case DELETED -> new Message("time.delete.done", false);
            case LOCKED -> new Message("timesheet.locked", true);
            case GONE -> new Message("time.entry.gone", true);
        };
        refresh();
    }

    // --- submitting --------------------------------------------------------------------------------

    private void submitClicked() {
        if (sheet == null) {
            return;
        }
        SubmitBlocker blocker = sheet.submitBlocker();
        if (blocker != null) {
            message = new Message(blockedKey(blocker), true);
            refresh();
            return;
        }
        openSubmitDialog();
    }

    /** The message for a blocked submission; a rejected timesheet talks about resubmitting. */
    private String blockedKey(SubmitBlocker blocker) {
        boolean resubmit = sheet != null && sheet.status() == TimesheetStatus.REJECTED
                && blocker != SubmitBlocker.NOT_DRAFT;
        return (resubmit ? "timesheet.resubmit.blocked." : "timesheet.submit.blocked.") + blocker.name();
    }

    /** Asks for confirmation, because a submitted timesheet can no longer be changed by the employee. */
    private void openSubmitDialog() {
        String prefix = sheet.status() == TimesheetStatus.REJECTED ? "timesheet.resubmit" : "timesheet.submit";
        Dialog dialog = new Dialog();
        dialog.setWidth("min(34rem, 92vw)");
        dialog.setHeaderTitle(getTranslation(prefix + ".title"));
        Paragraph question = new Paragraph(getTranslation(prefix + ".confirm"));
        question.addClassName("time-dialog-text");
        DialogError error = new DialogError();
        VerticalLayout body = new VerticalLayout(question, error);
        body.setPadding(false);
        body.addClassName("time-dialog-content");
        dialog.add(body);

        Button cancel = new Button(getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("submit-cancel");
        cancel.addClassName("time-dialog-button");
        Button confirm = new Button(getTranslation(prefix + ".action"));
        confirm.addThemeVariants(ButtonVariant.PRIMARY);
        confirm.setTestId("submit-confirm");
        confirm.addClassName("time-dialog-button");
        confirm.addClickListener(event -> submit(dialog, error, prefix));
        dialog.getFooter().add(cancel, confirm);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private void submit(Dialog dialog, Div error, String prefix) {
        try {
            submissionService.submit(employeeId, month, zone);
            message = new Message(prefix + ".done", false);
        } catch (SubmissionRejectedException rejected) {
            message = new Message(blockedKey(rejected.getBlocker()), true);
        } catch (DataAccessException e) {
            log.error("Submitting the timesheet {} of employee {} failed", month, employeeId, e);
            error.setText(getTranslation(prefix + ".failed"));
            error.setVisible(true); // stays open: Submit again to retry
            return;
        }
        dialog.close();
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
        addEntry.setText(getTranslation("timesheet.add"));
        updatingSelector = true;
        List<YearMonth> months = selectableMonths(latest);
        monthSelect.setItems(months);
        monthSelect.setItemLabelGenerator(ym -> monthLabel(ym, locale));
        monthSelect.setValue(month);
        updatingSelector = false;

        messageBox.show(message == null ? null : getTranslation(message.key()), message != null && message.error());

        loadErrorBox.update(loadFailed, getTranslation("timesheet.loadFailed"), getTranslation("time.retry"));
        boolean showSheet = !loadFailed && sheet != null;
        content.setVisible(showSheet);
        addEntry.setEnabled(showSheet && sheet.editable());
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
        regionNote.show(sheet);
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
            case SUBMITTED -> sheet.submittedAt() == null ? getTranslation("timesheet.status.SUBMITTED.nodate")
                    : sheet.rejectedAt() == null
                            ? getTranslation("timesheet.status.SUBMITTED", date(sheet.submittedAt(), locale))
                            : getTranslation("timesheet.status.SUBMITTED.resubmitted", date(sheet.submittedAt(), locale),
                                    date(sheet.rejectedAt(), locale));
            case APPROVED -> sheet.approvedByName() == null
                    ? getTranslation("timesheet.status.APPROVED.noone", date(sheet.approvedAt(), locale))
                    : getTranslation("timesheet.status.APPROVED", date(sheet.approvedAt(), locale),
                            sheet.approvedByName());
            case REJECTED -> sheet.rejectionReason() == null
                    ? getTranslation("timesheet.status.REJECTED.noreason", date(sheet.rejectedAt(), locale))
                    : getTranslation("timesheet.status.REJECTED", date(sheet.rejectedAt(), locale),
                            sheet.rejectionReason());
        });
        // A draft is submitted once its month is over (UC-006); a rejected timesheet is corrected and resubmitted (UC-008).
        boolean offersSubmit = status == TimesheetStatus.DRAFT || status == TimesheetStatus.REJECTED;
        boolean monthOver = sheet.submitBlocker() != SubmitBlocker.MONTH_NOT_ENDED;
        submit.setText(getTranslation(status == TimesheetStatus.REJECTED ? "timesheet.resubmit" : "timesheet.submit"));
        submit.setVisible(offersSubmit);
        submit.setEnabled(offersSubmit && monthOver);
        statusBox.setClassName("timesheet-status-rejected", status == TimesheetStatus.REJECTED);
        String note = status == TimesheetStatus.REJECTED ? getTranslation("timesheet.resubmit.hint")
                : !monthOver ? getTranslation("timesheet.submit.notYet", date(month.plusMonths(1).atDay(1)
                        .atStartOfDay(zone).toInstant(), locale))
                        : "";
        submitNote.setText(note);
        submitNote.setVisible(!note.isEmpty());
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
