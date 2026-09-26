package com.stubu.specdriven.admin;

import static com.stubu.specdriven.base.TableCells.cell;

import com.stubu.specdriven.base.DialogError;
import com.stubu.specdriven.base.LoadErrorBox;
import com.stubu.specdriven.base.MainLayout;
import com.stubu.specdriven.base.MessageBox;
import com.stubu.specdriven.employee.EditConflictException;
import com.stubu.specdriven.holiday.HolidayNotFoundException;
import com.stubu.specdriven.holiday.HolidayValidationException;
import com.stubu.specdriven.holiday.HolidayValidationException.Problem;
import com.stubu.specdriven.holiday.PublicHoliday;
import com.stubu.specdriven.holiday.PublicHolidayService;
import com.stubu.specdriven.region.RegionNotFoundException;
import com.stubu.specdriven.region.RegionRow;
import com.stubu.specdriven.region.RegionService;
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
import java.util.Optional;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * Public holidays for administrators (UC-012, UC-017): the holidays of one region by date, a selector for the region,
 * the actions to add, edit and delete holidays, and the dialog to manage the regions. Holidays are shown in the
 * month views of the employees of the region; they never change worked time.
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
    private final transient RegionService regionService;
    private final transient TimeEntryService entryService;
    private final Long adminId;
    private transient List<PublicHoliday> holidays = List.of();
    private transient List<RegionRow> regions = List.of();
    private Long chosenRegionId;
    private boolean loadFailed;
    private boolean initialYearChosen;
    private Message message;

    private final H2 heading = new H2();
    private final Button add = new Button(VaadinIcon.PLUS.create());
    private final Select<RegionRow> region = new Select<>();
    private final Button manageRegions = new Button();
    private final Select<Integer> year = new Select<>();
    private final Div toolbar = new Div();
    private final MessageBox messageBox = new MessageBox();
    private final LoadErrorBox loadErrorBox = new LoadErrorBox(this::refresh);
    private final Div emptyHint = new Div();
    private final Div list = new Div();
    private boolean updatingYear;
    private boolean updatingRegion;

    public PublicHolidayView(AuthenticationContext authenticationContext, PublicHolidayService service,
            RegionService regionService, TimeEntryService entryService) {
        this.service = service;
        this.regionService = regionService;
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
        region.setTestId("holiday-region");
        region.setItemLabelGenerator(RegionRow::name);
        region.addValueChangeListener(event -> {
            if (!updatingRegion && event.getValue() != null) {
                chosenRegionId = event.getValue().id();
                render();
            }
        });
        manageRegions.setTestId("manage-regions");
        manageRegions.addThemeVariants(ButtonVariant.TERTIARY);
        manageRegions.addClickListener(event -> new RegionManagementDialog(regionService, adminId, this::refresh).open(this));
        year.setTestId("holiday-year");
        year.setEmptySelectionAllowed(true);
        year.setItemLabelGenerator(value -> value == null ? getTranslation("holidays.allYears") : String.valueOf(value));
        year.addValueChangeListener(event -> {
            if (!updatingYear) {
                render();
            }
        });
        emptyHint.addClassName("time-empty");
        emptyHint.setTestId("no-holidays");
        list.addClassNames("approvals-list", "holiday-list");
        list.setTestId("holidays");
        list.getElement().setAttribute("role", "table");

        toolbar.addClassName("holiday-toolbar");
        toolbar.add(region, year, manageRegions);
        add(heading, add, messageBox, loadErrorBox, toolbar, emptyHint, list);
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
            regions = regionService.list(adminId);
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

        messageBox.show(message == null ? null : getTranslation(message.key(), message.parameters()), message != null && message.error());

        loadErrorBox.update(loadFailed, getTranslation("holidays.loadFailed"), getTranslation("time.retry"));
        manageRegions.setText(getTranslation("regions.manage"));
        renderRegions();
        add.setEnabled(!loadFailed && chosenRegion().isPresent());
        renderYears();
        toolbar.setVisible(!loadFailed);

        Integer chosen = year.getValue();
        String regionName = chosenRegion().map(RegionRow::name).orElse("");
        List<PublicHoliday> shown = holidaysOfChosenRegion().stream().filter(holiday -> chosen == null
                || holiday.getDate().getYear() == chosen).toList();
        emptyHint.setText(chosen == null ? getTranslation("holidays.none", regionName)
                : getTranslation("holidays.noneInYear", regionName, chosen));
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
            Button edit = action("holidays.edit", "edit-holiday", holiday, ButtonVariant.TERTIARY);
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

    private Optional<RegionRow> chosenRegion() {
        return regions.stream().filter(candidate -> chosenRegionId != null && candidate.id() == chosenRegionId)
                .findFirst();
    }

    private List<PublicHoliday> holidaysOfChosenRegion() {
        return holidays.stream().filter(holiday -> chosenRegionId != null
                && holiday.getRegionId().longValue() == chosenRegionId.longValue()).toList();
    }

    /** The regions to choose from; the chosen one stays chosen, else the first region (by name) is. */
    private void renderRegions() {
        updatingRegion = true;
        region.setLabel(getTranslation("holidays.region"));
        region.setItems(regions);
        if (chosenRegion().isEmpty()) {
            chosenRegionId = regions.isEmpty() ? null : regions.get(0).id();
        }
        region.setValue(chosenRegion().orElse(null));
        updatingRegion = false;
    }

    /** The years with holidays in the chosen region and the current year; the current year is chosen the first time. */
    private void renderYears() {
        int currentYear = entryService.currentDate(entryService.defaultZone()).getYear();
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.add(currentYear);
        holidaysOfChosenRegion().forEach(holiday -> years.add(holiday.getDate().getYear()));
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
        long regionId = editing ? existing.getRegionId() : chosenRegionId;
        TextField regionShown = new TextField(getTranslation("holidays.region"));
        regionShown.setTestId("holiday-region-shown");
        regionShown.setReadOnly(true);
        regionShown.setWidthFull();
        regions.stream().filter(candidate -> candidate.id() == regionId).findFirst()
                .ifPresent(shown -> regionShown.setValue(shown.name()));
        if (editing) {
            date.setValue(existing.getDate());
            name.setValue(existing.getName());
        }
        DialogError error = new DialogError();
        Div body = new Div(regionShown, date, name, error);
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
                        name.getValue(), existing.getVersion())
                        : service.add(adminId, regionId, date.getValue(), name.getValue());
                message = editing ? new Message("holidays.updated", false)
                        : new Message("holidays.added", false, saved.getName(), formatted(saved.getDate()));
            } catch (HolidayValidationException invalid) {
                show(invalid, date, name);
                return;
            } catch (HolidayNotFoundException | RegionNotFoundException gone) {
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
        DialogError error = new DialogError();
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
