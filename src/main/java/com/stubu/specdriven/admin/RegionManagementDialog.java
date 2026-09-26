package com.stubu.specdriven.admin;

import com.stubu.specdriven.base.DialogError;
import com.stubu.specdriven.base.MessageBox;
import com.stubu.specdriven.employee.EditConflictException;
import com.stubu.specdriven.region.Region;
import com.stubu.specdriven.region.RegionInUseException;
import com.stubu.specdriven.region.RegionNotFoundException;
import com.stubu.specdriven.region.RegionRow;
import com.stubu.specdriven.region.RegionService;
import com.stubu.specdriven.region.RegionValidationException;
import com.stubu.specdriven.region.RegionValidationException.Problem;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * The dialog to add, rename and delete regions (UC-017), opened from the public holidays page. It stays open while the
 * administrator works, shows what happened at its top, and tells the page after every change so that the region
 * selector of the page is up to date.
 */
class RegionManagementDialog {

    private static final Logger log = LoggerFactory.getLogger(RegionManagementDialog.class);
    private static final int MAX_LENGTH = 100;

    private final RegionService service;
    private final long adminId;
    private final Runnable changed;

    private Component owner;
    private final MessageBox message = new MessageBox();
    private final DialogError loadError = new DialogError();
    private final DialogError addError = new DialogError();
    private final TextField newName = new TextField();
    private final Div list = new Div();

    /**
     * @param changed called after every stored change, so the page can reload its regions
     */
    RegionManagementDialog(RegionService service, long adminId, Runnable changed) {
        this.service = service;
        this.adminId = adminId;
        this.changed = changed;
    }

    void open(Component owner) {
        this.owner = owner;
        Dialog dialog = new Dialog();
        dialog.setWidth("min(40rem, 94vw)");
        dialog.setHeaderTitle(text("regions.title"));

        Paragraph intro = new Paragraph(text("regions.intro"));
        intro.addClassName("time-dialog-text");
        newName.setLabel(text("regions.new"));
        newName.setTestId("region-name-new");
        newName.setMaxLength(MAX_LENGTH);
        newName.setClearButtonVisible(false);
        newName.setValue("");
        newName.setInvalid(false);
        newName.setWidthFull();
        Button add = new Button(text("regions.add"));
        add.setTestId("region-add");
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClickListener(event -> add());
        Div addRow = new Div(newName, add);
        addRow.addClassName("region-add");

        list.addClassName("region-list");
        list.setTestId("regions");
        list.getElement().setAttribute("role", "list");
        list.getElement().setAttribute("aria-label", text("regions.title"));
        message.setTestId("region-message");
        addError.setTestId("region-error");
        Div body = new Div(intro, message, addRow, addError, loadError, list);
        body.addClassNames("time-dialog-content", "review-dialog-content");
        dialog.add(body);

        Button close = new Button(text("regions.close"), event -> dialog.close());
        close.addThemeVariants(ButtonVariant.TERTIARY);
        close.setTestId("region-close");
        close.addClassName("time-dialog-button");
        dialog.getFooter().add(close);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
        render();
    }

    private String text(String key, Object... parameters) {
        return owner.getTranslation(key, parameters);
    }

    private void render() {
        list.removeAll();
        List<RegionRow> rows;
        try {
            rows = service.list(adminId);
            loadError.setVisible(false);
        } catch (DataAccessException e) {
            log.error("Could not load the regions for administrator {}", adminId, e);
            loadError.show(text("regions.loadFailed"));
            return;
        }
        for (RegionRow region : rows) {
            Span name = new Span(region.name());
            name.addClassName("region-name");
            Span counts = new Span(text("regions.counts", region.employees(), region.holidays()));
            counts.addClassName("region-counts");
            Button rename = rowButton("regions.rename", "region-rename", region, ButtonVariant.TERTIARY);
            rename.addClickListener(event -> openRename(region));
            Button delete = rowButton("regions.delete", "region-delete", region, ButtonVariant.ERROR,
                    ButtonVariant.TERTIARY);
            delete.addClickListener(event -> openDelete(region));
            Div actions = new Div(rename, delete);
            actions.addClassName("manage-actions");
            Div row = new Div(new Div(name, counts), actions);
            row.addClassName("region-row");
            row.setTestId("region-row");
            row.getElement().setAttribute("role", "listitem");
            list.add(row);
        }
    }

    private Button rowButton(String key, String testId, RegionRow region, ButtonVariant... variants) {
        Button button = new Button(text(key));
        button.setTestId(testId);
        button.addThemeVariants(variants);
        button.addClassName("review-button");
        button.getElement().setAttribute("aria-label", text(key + ".label", region.name()));
        return button;
    }

    // --- add ---------------------------------------------------------------------------------------

    private void add() {
        newName.setInvalid(false);
        addError.setVisible(false);
        try {
            Region saved = service.add(adminId, newName.getValue());
            newName.setValue("");
            done("regions.added", saved.getName());
        } catch (RegionValidationException invalid) {
            showInvalid(invalid, newName);
        } catch (DataAccessException e) {
            log.error("Adding a region failed for administrator {}", adminId, e);
            addError.show(text("manage.saveFailed"));
        }
    }

    private void showInvalid(RegionValidationException invalid, TextField field) {
        String key = invalid.has(Problem.NAME_REQUIRED) ? "regions.error.NAME_REQUIRED"
                : invalid.has(Problem.NAME_TOO_LONG) ? "regions.error.NAME_TOO_LONG" : "regions.error.NAME_TAKEN";
        field.setErrorMessage(text(key));
        field.setInvalid(true);
    }

    /** A change is stored: say so, redraw the list and let the page reload its regions. */
    private void done(String key, Object... parameters) {
        message.show(text(key, parameters), false);
        render();
        changed.run();
    }

    private void failed(String key, Object... parameters) {
        message.show(text(key, parameters), true);
        render();
        changed.run();
    }

    // --- rename ------------------------------------------------------------------------------------

    private void openRename(RegionRow region) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(30rem, 94vw)");
        dialog.setHeaderTitle(text("regions.rename.title"));
        TextField name = new TextField(text("regions.name"));
        name.setTestId("region-name");
        name.setMaxLength(MAX_LENGTH);
        name.setRequiredIndicatorVisible(true);
        name.setValue(region.name());
        name.setWidthFull();
        DialogError error = new DialogError();
        Div body = new Div(name, error);
        body.addClassNames("time-dialog-content", "review-dialog-content");
        dialog.add(body);

        Button cancel = new Button(text("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("region-rename-cancel");
        cancel.addClassName("time-dialog-button");
        Button save = new Button(text("manage.save"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.setTestId("region-rename-save");
        save.addClassName("time-dialog-button");
        save.addClickListener(event -> {
            name.setInvalid(false);
            error.setVisible(false);
            try {
                service.rename(adminId, region.id(), name.getValue(), region.version());
                dialog.close();
                done("regions.renamed");
            } catch (RegionValidationException invalid) {
                showInvalid(invalid, name);
            } catch (RegionNotFoundException gone) {
                dialog.close();
                failed("regions.gone");
            } catch (EditConflictException conflict) {
                dialog.close();
                failed("regions.conflict");
            } catch (DataAccessException e) {
                log.error("Renaming region {} failed for administrator {}", region.id(), adminId, e);
                error.show(text("manage.saveFailed")); // stays open: Save again to retry
            }
        });
        dialog.getFooter().add(cancel, save);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    // --- delete ------------------------------------------------------------------------------------

    private void openDelete(RegionRow region) {
        Dialog dialog = new Dialog();
        dialog.setWidth("min(34rem, 92vw)");
        dialog.setHeaderTitle(text("regions.delete.title"));
        Paragraph question = new Paragraph(text("regions.delete.confirm", region.name()));
        question.addClassName("time-dialog-text");
        DialogError error = new DialogError();
        Div body = new Div(question, error);
        body.addClassNames("time-dialog-content", "review-dialog-content");
        dialog.add(body);

        Button cancel = new Button(text("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("region-delete-cancel");
        cancel.addClassName("time-dialog-button");
        Button confirm = new Button(text("regions.delete"));
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        confirm.setTestId("region-delete-confirm");
        confirm.addClassName("time-dialog-button");
        confirm.addClickListener(event -> {
            try {
                service.delete(adminId, region.id());
                dialog.close();
                done("regions.deleted");
            } catch (RegionInUseException inUse) {
                dialog.close();
                failed("regions.inUse." + inUse.getReason().name(), inUse.getCount());
            } catch (RegionNotFoundException gone) {
                dialog.close();
                failed("regions.gone");
            } catch (DataAccessException e) {
                log.error("Deleting region {} failed for administrator {}", region.id(), adminId, e);
                error.show(text("manage.saveFailed")); // stays open: Delete again to retry
            }
        });
        dialog.getFooter().add(cancel, confirm);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }
}
