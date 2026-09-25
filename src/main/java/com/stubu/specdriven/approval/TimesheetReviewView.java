package com.stubu.specdriven.approval;

import com.stubu.specdriven.base.FlashMessage;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.monthlytimesheet.MonthTimeline;
import com.stubu.specdriven.monthlytimesheet.MonthlyTimesheet;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.timesheet.TimesheetStatus;
import com.stubu.specdriven.timetracking.DurationFormat;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
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
 * A manager reviews one submitted timesheet (UC-007): all days with their work periods, the totals and the
 * status, then approves it or rejects it with a reason. The page is read-only; entries are never edited here.
 */
@Route(value = "approvals/review", layout = MainLayout.class)
@RolesAllowed({ "MANAGER", "ADMIN" })
public class TimesheetReviewView extends VerticalLayout implements HasUrlParameter<Long>, HasDynamicTitle,
        LocaleChangeObserver {

    private static final Logger log = LoggerFactory.getLogger(TimesheetReviewView.class);
    private static final String DIALOG_WIDTH = "min(34rem, 92vw)";

    /** The last message, kept as key so it can be translated again. */
    private record Message(String key, boolean error) {
    }

    private final transient TimesheetReviewService service;
    private final transient TimeEntryService entryService;
    private final Long reviewerId;
    private ZoneId zone;
    private Long timesheetId;
    private transient ReviewDetails details;
    private boolean loadFailed;
    private Message message;

    private final Button back = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final H2 heading = new H2();
    private final Div messageBox = new Div();
    private final Div loadErrorBox = new Div();
    private final Span loadError = new Span();
    private final Button retry = new Button();
    private final VerticalLayout content = new VerticalLayout();
    private final Div statusBox = new Div();
    private final Badge statusBadge = new Badge();
    private final Span statusText = new Span();
    private final Button approve = new Button();
    private final Button reject = new Button();
    private final Span actionNote = new Span();
    private final Span total = new Span();
    private final Span breaks = new Span();
    private final MonthTimeline timeline = new MonthTimeline();

    public TimesheetReviewView(AuthenticationContext authenticationContext, TimesheetReviewService service,
            TimeEntryService entryService) {
        this.service = service;
        this.entryService = entryService;
        this.zone = entryService.defaultZone();
        this.reviewerId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);

        addClassNames("timesheet-view", "review-view");
        setPadding(true);

        back.setTestId("back");
        back.addThemeVariants(ButtonVariant.TERTIARY);
        back.addClickListener(event -> getUI().ifPresent(ui -> ui.navigate(ApprovalsView.class)));

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

        statusBadge.setTestId("timesheet-status");
        statusText.setTestId("status-text");
        approve.setTestId("approve");
        approve.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SUCCESS);
        approve.addClickListener(event -> openApproveDialog());
        reject.setTestId("reject");
        reject.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        reject.addClickListener(event -> openRejectDialog());
        actionNote.addClassName("status-note");
        actionNote.setTestId("review-note");
        Div statusLine = new Div(statusBadge, statusText);
        statusLine.addClassName("status-line");
        Div actions = new Div(approve, reject, actionNote);
        actions.addClassName("status-actions");
        statusBox.add(statusLine, actions);
        statusBox.addClassName("timesheet-status");

        total.addClassNames("time-total", "month-total");
        total.setTestId("month-total");
        breaks.addClassName("time-total");
        breaks.setTestId("month-break");
        Div totals = new Div(total, breaks);
        totals.addClassName("time-totals");
        timeline.setReadOnly(true);

        content.setPadding(false);
        content.setSpacing(true);
        content.add(statusBox, totals, timeline);
        add(back, heading, messageBox, loadErrorBox, content);
    }

    // --- loading -----------------------------------------------------------------------------------

    @Override
    public void setParameter(BeforeEvent event, Long timesheetId) {
        this.timesheetId = timesheetId;
        try {
            load();
        } catch (ReviewNotAllowedException notAllowed) {
            // Also for a timesheet that does not exist: the two are not told apart.
            FlashMessage.set("approvals.noPermission", true);
            event.forwardTo(ApprovalsView.class);
            return;
        }
        if (isAttached()) {
            render();
        }
    }

    /** The browser's time zone decides which day an entry belongs to. */
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        attachEvent.getUI().getPage().retrieveExtendedClientDetails(clientDetails -> {
            try {
                ZoneId browserZone = ZoneId.of(clientDetails.getTimeZoneId());
                if (!browserZone.equals(zone)) {
                    zone = browserZone;
                    refresh();
                }
            } catch (DateTimeException | NullPointerException unknownZone) {
                log.debug("Keeping the server time zone, the browser reported {}", clientDetails.getTimeZoneId());
            }
        });
    }

    private void refresh() {
        try {
            load();
        } catch (ReviewNotAllowedException notAllowed) {
            FlashMessage.set("approvals.noPermission", true);
            getUI().ifPresent(ui -> ui.navigate(ApprovalsView.class));
            return;
        }
        render();
    }

    private void load() {
        if (reviewerId == null || timesheetId == null) {
            throw new ReviewNotAllowedException();
        }
        try {
            details = service.details(reviewerId, timesheetId, zone);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load timesheet {} for review by {}", timesheetId, reviewerId, e);
            loadFailed = true;
        }
    }

    @Override
    public String getPageTitle() {
        return getTranslation("review.pageTitle");
    }

    // --- rendering ---------------------------------------------------------------------------------

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    private void render() {
        Locale locale = getLocale();
        back.setText(getTranslation("review.back"));
        back.getElement().setAttribute("aria-label", getTranslation("review.back"));

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

        loadError.setText(getTranslation("review.loadFailed"));
        retry.setText(getTranslation("time.retry"));
        loadErrorBox.setVisible(loadFailed);
        boolean show = !loadFailed && details != null;
        content.setVisible(show);
        heading.setVisible(show);
        if (!show) {
            return;
        }
        MonthlyTimesheet sheet = details.sheet();
        heading.setText(getTranslation("review.title", details.employeeName(),
                ApprovalsView.month(sheet.period(), locale)));
        renderStatus(sheet, locale);
        total.setText(getTranslation("timesheet.total", DurationFormat.format(sheet.totalWorked())));
        breaks.setText(getTranslation("time.break", DurationFormat.format(sheet.totalBreaks())));
        timeline.show(sheet, zone, entryService.now(), entryService.currentDate(zone));
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
                    : getTranslation("timesheet.status.SUBMITTED", date(sheet.submittedAt(), locale));
            case APPROVED -> sheet.approvedByName() == null
                    ? getTranslation("timesheet.status.APPROVED.noone", date(sheet.approvedAt(), locale))
                    : getTranslation("timesheet.status.APPROVED", date(sheet.approvedAt(), locale),
                            sheet.approvedByName());
            case REJECTED -> sheet.rejectionReason() == null
                    ? getTranslation("timesheet.status.REJECTED.noreason", date(sheet.rejectedAt(), locale))
                    : getTranslation("timesheet.status.REJECTED", date(sheet.rejectedAt(), locale),
                            sheet.rejectionReason());
        });
        boolean pending = status == TimesheetStatus.SUBMITTED;
        approve.setText(getTranslation("review.approve"));
        reject.setText(getTranslation("review.reject"));
        approve.setEnabled(pending);
        reject.setEnabled(pending);
        actionNote.setText(pending ? "" : getTranslation("review.notPending"));
        actionNote.setVisible(!pending);
    }

    private String date(Instant instant, Locale locale) {
        return instant == null ? "" : DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                .withZone(zone).format(instant);
    }

    // --- approving ---------------------------------------------------------------------------------

    private void openApproveDialog() {
        Dialog dialog = newDialog(getTranslation("review.approve.title"));
        Paragraph question = new Paragraph(getTranslation("review.approve.confirm"));
        question.addClassName("time-dialog-text");
        Paragraph subject = new Paragraph(getTranslation("review.title", details.employeeName(),
                ApprovalsView.month(details.sheet().period(), getLocale())));
        subject.addClassName("time-dialog-subject");
        Div error = errorBox();
        dialog.add(dialogContent(question, subject, error));

        Button cancel = dialogButton(getTranslation("time.cancel"), "approve-cancel", ButtonVariant.TERTIARY);
        cancel.addClickListener(event -> dialog.close());
        Button confirm = dialogButton(getTranslation("review.approve.action"), "approve-confirm",
                ButtonVariant.PRIMARY, ButtonVariant.SUCCESS);
        confirm.addClickListener(event -> approve(dialog, error));
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void approve(Dialog dialog, Div error) {
        try {
            service.approve(reviewerId, timesheetId);
        } catch (ReviewNotAllowedException notAllowed) {
            leaveWith(dialog, "approvals.noPermission");
            return;
        } catch (ReviewStatusException decided) {
            decidedMeanwhile(dialog);
            return;
        } catch (DataAccessException e) {
            log.error("Approving timesheet {} failed for reviewer {}", timesheetId, reviewerId, e);
            showError(error, getTranslation("review.approve.failed")); // stays open: Approve again to retry
            return;
        }
        FlashMessage.set("approvals.approved", false);
        dialog.close();
        getUI().ifPresent(ui -> ui.navigate(ApprovalsView.class));
    }

    // --- rejecting ---------------------------------------------------------------------------------

    private void openRejectDialog() {
        Dialog dialog = newDialog(getTranslation("review.reject.title"));
        TextArea reason = new TextArea(getTranslation("review.reject.reason"));
        reason.setTestId("reject-reason");
        reason.setRequiredIndicatorVisible(true);
        reason.setMaxLength(TimesheetReviewService.MAX_REASON_LENGTH);
        reason.setHelperText(getTranslation("review.reject.reasonHelper"));
        reason.setWidthFull();
        reason.setMinHeight("7rem");
        Checkbox confirmation = new Checkbox(getTranslation("review.reject.confirmBox"));
        confirmation.setTestId("reject-confirmation");
        Div error = errorBox();
        dialog.add(dialogContent(reason, confirmation, error));

        Button cancel = dialogButton(getTranslation("time.cancel"), "reject-cancel", ButtonVariant.TERTIARY);
        cancel.addClickListener(event -> dialog.close());
        Button confirm = dialogButton(getTranslation("review.reject.action"), "reject-confirm",
                ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        confirm.addClickListener(event -> reject(dialog, error, reason, confirmation));
        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }

    private void reject(Dialog dialog, Div error, TextArea reason, Checkbox confirmation) {
        reason.setInvalid(false);
        error.setVisible(false);
        if (reason.getValue() == null || reason.getValue().isBlank()) {
            reason.setErrorMessage(getTranslation("review.reject.reasonRequired"));
            reason.setInvalid(true);
            reason.focus();
            return;
        }
        if (!confirmation.getValue()) {
            showError(error, getTranslation("review.reject.confirmRequired"));
            return;
        }
        try {
            service.reject(reviewerId, timesheetId, reason.getValue());
        } catch (InvalidReasonException invalid) {
            reason.setErrorMessage(getTranslation(invalid.getProblem() == InvalidReasonException.Problem.REQUIRED
                    ? "review.reject.reasonRequired" : "review.reject.tooLong"));
            reason.setInvalid(true);
            return;
        } catch (ReviewNotAllowedException notAllowed) {
            leaveWith(dialog, "approvals.noPermission");
            return;
        } catch (ReviewStatusException decided) {
            decidedMeanwhile(dialog);
            return;
        } catch (DataAccessException e) {
            log.error("Rejecting timesheet {} failed for reviewer {}", timesheetId, reviewerId, e);
            showError(error, getTranslation("review.reject.failed")); // stays open: Reject again to retry
            return;
        }
        FlashMessage.set("approvals.rejected", false);
        dialog.close();
        getUI().ifPresent(ui -> ui.navigate(ApprovalsView.class));
    }

    // --- shared ------------------------------------------------------------------------------------

    /** Somebody else decided in the meantime: show the timesheet as it is now. */
    private void decidedMeanwhile(Dialog dialog) {
        dialog.close();
        message = new Message("review.decided", true);
        refresh();
    }

    private void leaveWith(Dialog dialog, String messageKey) {
        FlashMessage.set(messageKey, true);
        dialog.close();
        getUI().ifPresent(ui -> ui.navigate(ApprovalsView.class));
    }

    private Dialog newDialog(String title) {
        Dialog dialog = new Dialog();
        dialog.setWidth(DIALOG_WIDTH);
        dialog.setHeaderTitle(title);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        return dialog;
    }

    private static Div dialogContent(com.vaadin.flow.component.Component... components) {
        Div body = new Div(components);
        body.addClassNames("time-dialog-content", "review-dialog-content");
        return body;
    }

    private static Button dialogButton(String text, String testId, ButtonVariant... variants) {
        Button button = new Button(text);
        button.addThemeVariants(variants);
        button.setTestId(testId);
        button.addClassName("time-dialog-button");
        return button;
    }

    private static Div errorBox() {
        Div error = new Div();
        error.addClassName("time-dialog-error");
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);
        return error;
    }

    private static void showError(Div error, String text) {
        error.setText(text);
        error.setVisible(true);
    }
}
