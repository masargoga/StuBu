package com.stubu.specdriven.admin;

import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.employee.EditConflictException;
import com.stubu.specdriven.holiday.HolidayNotFoundException;
import com.stubu.specdriven.holiday.HolidayValidationException;
import com.stubu.specdriven.holiday.HolidayValidationException.Problem;
import com.stubu.specdriven.holiday.PublicHoliday;
import com.stubu.specdriven.holiday.PublicHolidayService;
import com.stubu.specdriven.security.EmployeePrincipal;
import com.stubu.specdriven.timetracking.TimeEntryService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * Public holidays for administrators (UC-012): the configured holidays by date, and the actions to add, edit and
 * delete them. Holidays are shown in the month views of employees and managers; they never change worked time.
 */
@Route(value = PublicHolidayView.ROUTE, layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class PublicHolidayView extends VerticalLayout implements HasDynamicTitle, LocaleChangeObserver {

    public static final String ROUTE = "admin/holidays";
    private static final Logger log = LoggerFactory.getLogger(PublicHolidayView.class);

    /** The last message, kept as key and parameters so it can be translated again. */
    private record Message(String key, boolean error, Object... parameters) {
    }

    private final transient PublicHolidayService service;
    private final transient TimeEntryService entryService;
    private final Long adminId;
    private transient List<PublicHoliday> holidays = List.of();
    private boolean loadFailed;
    private boolean initialYearChosen;
    private Message message;

    private final H2 heading = new H2();
    private final Button add = new Button(VaadinIcon.PLUS.create());
    private final Select<Integer> year = new Select<>();
    private final Div messageBox = new Div();
    private final Div loadErrorBox = new Div();
    private final Span loadError = new Span();
    private final Button retry = new Button();
    private final Div emptyHint = new Div();
    private final Div list = new Div();
    private boolean updatingYear;

    public PublicHolidayView(AuthenticationContext authenticationContext, PublicHolidayService service,
            TimeEntryService entryService) {
        this.service = service;
        this.entryService = entryService;
        this.adminId = authenticationContext.getAuthenticatedUser(Object.class)
                .filter(EmployeePrincipal.class::isInstance).map(EmployeePrincipal.class::cast)
                .map(EmployeePrincipal::getEmployeeId).orElse(null);

        addClassNames("timesheet-view", "manage-view", "holiday-view");
        setPadding(true);

        add.setTestId("add-holiday");
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClassName("manage-add");
        add.addClickListener(event -> openForm(null));
        year.setTestId("holiday-year");
        year.setEmptySelectionAllowed(true);
        year.setItemLabelGenerator(value -> value == null ? getTranslation("holidays.allYears") : String.valueOf(value));
        year.addValueChangeListener(event -> {
            if (!updatingYear) {
                render();
            }
        });
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
        emptyHint.setTestId("no-holidays");
        list.addClassNames("approvals-list", "holiday-list");
        list.setTestId("holidays");
        list.getElement().setAttribute("role", "table");

        add(heading, add, messageBox, loadErrorBox, year, emptyHint, list);
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
            holidays = service.list(adminId);
            loadFailed = false;
        } catch (DataAccessException e) {
            log.error("Could not load the public holidays for administrator {}", adminId, e);
            loadFailed = true;
        }
    }

    @Override
    public String getPageTitle() {
        return getTranslation("holidays.title");
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        render();
    }

    // --- rendering ---------------------------------------------------------------------------------

    private void render() {
        Locale locale = getLocale();
        heading.setText(getTranslation("holidays.title"));
        add.setText(getTranslation("holidays.add"));

        if (message == null) {
            messageBox.setVisible(false);
        } else {
            var icon = (message.error() ? VaadinIcon.WARNING : VaadinIcon.CHECK_CIRCLE).create();
            icon.getElement().setAttribute("aria-hidden", "true");
            messageBox.removeAll();
            messageBox.add(icon, new Span(getTranslation(message.key(), message.parameters())));
            messageBox.setClassName("time-message-error", message.error());
            messageBox.getElement().setAttribute("role", message.error() ? "alert" : "status");
            messageBox.setVisible(true);
        }

        loadError.setText(getTranslation("holidays.loadFailed"));
        retry.setText(getTranslation("time.retry"));
        loadErrorBox.setVisible(loadFailed);
        add.setEnabled(!loadFailed);
        renderYears();
        year.setVisible(!loadFailed);

        Integer chosen = year.getValue();
        List<PublicHoliday> shown = holidays.stream().filter(holiday -> chosen == null
                || holiday.getDate().getYear() == chosen).toList();
        emptyHint.setText(chosen == null ? getTranslation("holidays.none") : getTranslation("holidays.noneInYear",
                chosen));
        emptyHint.setVisible(!loadFailed && shown.isEmpty());
        list.setVisible(!loadFailed && !shown.isEmpty());
        if (!list.isVisible()) {
            return;
        }

        list.removeAll();
        list.getElement().setAttribute("aria-label", getTranslation("holidays.title"));
        Div head = new Div(cell("columnheader", getTranslation("holidays.date"), null),
                cell("columnheader", getTranslation("holidays.name"), null),
                cell("columnheader", getTranslation("approvals.action"), null));
        head.addClassName("approval-head");
        head.getElement().setAttribute("role", "row");
        list.add(head);
        DateTimeFormatter format = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale);
        for (PublicHoliday holiday : shown) {
            Span date = cell("cell", holiday.getDate().format(format), null);
            date.addClassName("approval-employee");
            Div actions = new Div();
            actions.addClassNames("approval-cell", "manage-actions");
            actions.getElement().setAttribute("role", "cell");
            Button edit = action("holidays.edit", "edit-holiday", holiday, ButtonVariant.PRIMARY);
            edit.addClickListener(event -> openForm(holiday));
            Button delete = action("holidays.delete", "delete-holiday", holiday, ButtonVariant.ERROR,
                    ButtonVariant.TERTIARY);
            delete.addClickListener(event -> openDelete(holiday));
            actions.add(edit, delete);
            Div row = new Div(date, cell("cell", holiday.getName(), getTranslation("holidays.name")), actions);
            row.addClassNames("approval-row", "manage-row");
            row.setTestId("holiday-row");
            row.getElement().setAttribute("role", "row");
            list.add(row);
        }
    }

    /** The years with holidays and the current year; the current year is chosen the first time. */
    private void renderYears() {
        int currentYear = entryService.currentDate(entryService.defaultZone()).getYear();
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.add(currentYear);
        holidays.forEach(holiday -> years.add(holiday.getDate().getYear()));
        Integer keep = year.getValue();
        updatingYear = true;
        year.setLabel(getTranslation("holidays.year"));
        year.setEmptySelectionCaption(getTranslation("holidays.allYears"));
        year.setItems(List.copyOf(years));
        if (!initialYearChosen) {
            year.setValue(currentYear);
            initialYearChosen = true;
        } else if (keep != null && years.contains(keep)) {
            year.setValue(keep);
        }
        updatingYear = false;
    }

    private Button action(String key, String testId, PublicHoliday holiday, ButtonVariant... variants) {
        Button button = new Button(getTranslation(key));
        button.setTestId(testId);
        button.addThemeVariants(variants);
        button.addClassName("review-button");
        button.getElement().setAttribute("aria-label", getTranslation(key + ".label", holiday.getName()));
        return button;
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

    // --- add and edit ------------------------------------------------------------------------------

    private void openForm(PublicHoliday existing) {
        boolean editing = existing != null;
        Dialog dialog = new Dialog();
        dialog.setWidth("min(32rem, 94vw)");
        dialog.setHeaderTitle(getTranslation(editing ? "holidays.edit.title" : "holidays.add.title"));
        DatePicker date = new DatePicker(getTranslation("holidays.date"));
        date.setTestId("holiday-date");
        date.setLocale(getLocale()); // month and weekday names and the date format follow the language of the page
        date.setRequiredIndicatorVisible(true);
        date.setWidthFull();
        TextField name = new TextField(getTranslation("holidays.name"));
        name.setTestId("holiday-name");
        name.setMaxLength(255);
        name.setRequiredIndicatorVisible(true);
        name.setWidthFull();
        if (editing) {
            date.setValue(existing.getDate());
            name.setValue(existing.getName());
        }
        Div error = new Div();
        error.addClassName("time-dialog-error");
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);
        Div body = new Div(date, name, error);
        body.addClassNames("time-dialog-content", "review-dialog-content");
        dialog.add(body);

        Button cancel = new Button(getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("holiday-cancel");
        cancel.addClassName("time-dialog-button");
        Button save = new Button(getTranslation("manage.save"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.setTestId("holiday-save");
        save.addClassName("time-dialog-button");
        save.addClickListener(event -> {
            date.setInvalid(false);
            name.setInvalid(false);
            error.setVisible(false);
            try {
                PublicHoliday saved = editing ? service.update(adminId, existing.getId(), date.getValue(),
                        name.getValue(), existing.getVersion()) : service.add(adminId, date.getValue(), name.getValue());
                message = editing ? new Message("holidays.updated", false)
                        : new Message("holidays.added", false, saved.getName(), formatted(saved.getDate()));
            } catch (HolidayValidationException invalid) {
                show(invalid, date, name);
                return;
            } catch (HolidayNotFoundException gone) {
                message = new Message("holidays.gone", true);
            } catch (EditConflictException conflict) {
                message = new Message("holidays.conflict", true);
            } catch (DataAccessException e) {
                log.error("Saving a public holiday failed for administrator {}", adminId, e);
                error.setText(getTranslation("manage.saveFailed"));
                error.setVisible(true); // stays open: Save again to retry
                return;
            }
            dialog.close();
            refresh();
        });
        dialog.getFooter().add(cancel, save);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private void show(HolidayValidationException invalid, DatePicker date, TextField name) {
        if (invalid.has(Problem.DATE_INVALID)) {
            date.setErrorMessage(getTranslation("holidays.error.DATE_INVALID"));
            date.setInvalid(true);
        }
        if (invalid.has(Problem.DATE_TAKEN)) {
            date.setErrorMessage(getTranslation("holidays.error.DATE_TAKEN", formatted(invalid.getDate())));
            date.setInvalid(true);
        }
        if (invalid.has(Problem.NAME_REQUIRED)) {
            name.setErrorMessage(getTranslation("holidays.error.NAME_REQUIRED"));
            name.setInvalid(true);
        }
        if (invalid.has(Problem.NAME_TOO_LONG)) {
            name.setErrorMessage(getTranslation("holidays.error.NAME_TOO_LONG"));
            name.setInvalid(true);
        }
    }

    // --- delete ------------------------------------------------------------------------------------

    private void openDelete(PublicHoliday holiday) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(34rem, 92vw)");
        dialog.setHeaderTitle(getTranslation("holidays.delete.title"));
        Paragraph question = new Paragraph(getTranslation("holidays.delete.confirm", holiday.getName(),
                formatted(holiday.getDate())));
        question.addClassName("time-dialog-text");
        Div error = new Div();
        error.addClassName("time-dialog-error");
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);
        Div body = new Div(question, error);
        body.addClassNames("time-dialog-content", "review-dialog-content");
        dialog.add(body);

        Button cancel = new Button(getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("delete-cancel");
        cancel.addClassName("time-dialog-button");
        Button confirm = new Button(getTranslation("holidays.delete"));
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        confirm.setTestId("delete-confirm");
        confirm.addClassName("time-dialog-button");
        confirm.addClickListener(event -> {
            try {
                service.delete(adminId, holiday.getId());
                message = new Message("holidays.deleted", false);
            } catch (HolidayNotFoundException gone) {
                message = new Message("holidays.gone", true);
            } catch (DataAccessException e) {
                log.error("Deleting a public holiday failed for administrator {}", adminId, e);
                error.setText(getTranslation("manage.saveFailed"));
                error.setVisible(true); // stays open: Delete again to retry
                return;
            }
            dialog.close();
            refresh();
        });
        dialog.getFooter().add(cancel, confirm);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    private String formatted(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(getLocale()));
    }
}
