package com.stubu.specdriven.admin;

import com.stubu.specdriven.employee.Choice;
import com.stubu.specdriven.employee.EditConflictException;
import com.stubu.specdriven.employee.EmployeeAdminService;
import com.stubu.specdriven.employee.EmployeeInput;
import com.stubu.specdriven.employee.EmployeeNotFoundException;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.employee.EmployeeValidationException;
import com.stubu.specdriven.employee.EmployeeValidationException.Problem;
import com.stubu.specdriven.employee.Role;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.function.SerializableConsumer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * The form to add or edit an employee (UC-011). On editing the email is shown but cannot be changed, so that the
 * audit trail always refers to the same address. Problems the service finds are shown at the fields they belong to.
 */
class EmployeeFormDialog {

    /** What happened, so the page can tell the administrator. */
    enum Outcome {
        CREATED, UPDATED, GONE, CONFLICT
    }

    private static final Logger log = LoggerFactory.getLogger(EmployeeFormDialog.class);
    private static final int MAX_LENGTH = 255;

    private final EmployeeAdminService service;
    private final long adminId;

    EmployeeFormDialog(EmployeeAdminService service, long adminId) {
        this.service = service;
        this.adminId = adminId;
    }

    /**
     * Opens the form.
     *
     * @param existing    the employee to edit, or {@code null} to add one
     * @param departments the departments to choose from
     * @param regions     the regions to choose from; a new employee starts with the first one
     * @param managers    the possible managers
     * @param done        told the outcome and the employee's name once the change is stored
     */
    void open(Component owner, EmployeeRow existing, List<Choice> departments, List<Choice> regions,
            List<Choice> managers, SerializableConsumer<Result> done) {
        boolean editing = existing != null;
        Dialog dialog = new Dialog();
        dialog.setWidth("min(40rem, 94vw)");
        dialog.setHeaderTitle(owner.getTranslation(editing ? "manage.edit.title" : "manage.add.title"));

        TextField email = new TextField(owner.getTranslation("manage.email"));
        email.setTestId("employee-email");
        email.setMaxLength(320);
        email.setRequiredIndicatorVisible(!editing);
        email.setReadOnly(editing);
        email.setValue(editing ? existing.email() : "");
        if (editing) {
            email.setHelperText(owner.getTranslation("manage.email.fixed"));
        }
        TextField firstName = textField(owner, "manage.firstName", "employee-first-name");
        TextField lastName = textField(owner, "manage.lastName", "employee-last-name");
        Select<Role> role = new Select<>();
        role.setLabel(owner.getTranslation("manage.role"));
        role.setTestId("employee-role");
        role.setItems(Role.values());
        role.setItemLabelGenerator(value -> owner.getTranslation("role." + value.name()));
        role.setRequiredIndicatorVisible(true);
        Select<Choice> department = new Select<>();
        department.setLabel(owner.getTranslation("manage.department"));
        department.setTestId("employee-department");
        department.setItems(departments);
        department.setItemLabelGenerator(Choice::label);
        department.setRequiredIndicatorVisible(true);
        Select<Choice> region = new Select<>();
        region.setLabel(owner.getTranslation("manage.region"));
        region.setTestId("employee-region");
        region.setItems(regions);
        region.setItemLabelGenerator(Choice::label);
        region.setRequiredIndicatorVisible(true);
        region.setHelperText(owner.getTranslation("manage.region.helper"));
        if (!editing && !regions.isEmpty()) {
            region.setValue(regions.get(0));
        }
        Select<Choice> manager = new Select<>();
        manager.setLabel(owner.getTranslation("manage.manager"));
        manager.setTestId("employee-manager");
        manager.setItems(managers.stream().filter(choice -> !editing || choice.id() != existing.id()).toList());
        manager.setItemLabelGenerator(choice -> choice == null ? "" : choice.label());
        manager.setEmptySelectionAllowed(true);
        manager.setEmptySelectionCaption(owner.getTranslation("manage.manager.none"));
        manager.setHelperText(owner.getTranslation("manage.manager.helper"));
        if (editing) {
            firstName.setValue(existing.firstName());
            lastName.setValue(existing.lastName());
            role.setValue(existing.role());
            departments.stream().filter(choice -> choice.id() == existing.departmentId()).findFirst()
                    .ifPresent(department::setValue);
            regions.stream().filter(choice -> choice.id() == existing.regionId()).findFirst()
                    .ifPresent(region::setValue);
            managers.stream().filter(choice -> existing.managerId() != null && choice.id() == existing.managerId())
                    .findFirst().ifPresent(manager::setValue);
        }

        FormLayout form = new FormLayout(email, firstName, lastName, role, department, region, manager);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("34rem", 2));
        form.setColspan(email, 2);
        form.setColspan(region, 2);
        form.setColspan(manager, 2);
        Div error = new Div();
        error.addClassName("time-dialog-error");
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);
        Div body = new Div(form, error);
        body.addClassNames("time-dialog-content", "review-dialog-content");
        dialog.add(body);

        Button cancel = new Button(owner.getTranslation("time.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        cancel.setTestId("employee-cancel");
        cancel.addClassName("time-dialog-button");
        Button save = new Button(owner.getTranslation("manage.save"));
        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.setTestId("employee-save");
        save.addClassName("time-dialog-button");
        save.addClickListener(event -> {
            for (Component field : List.of(email, firstName, lastName, role, department, region, manager)) {
                ((com.vaadin.flow.component.shared.HasValidationProperties) field).setInvalid(false);
            }
            error.setVisible(false);
            EmployeeInput input = new EmployeeInput(email.getValue(), firstName.getValue(), lastName.getValue(),
                    role.getValue(), manager.getValue() == null ? null : manager.getValue().id(),
                    department.getValue() == null ? null : department.getValue().id(),
                    region.getValue() == null ? null : region.getValue().id(),
                    editing ? existing.version() : null);
            try {
                EmployeeRow saved = editing ? service.update(adminId, existing.id(), input)
                        : service.create(adminId, input);
                dialog.close();
                done.accept(new Result(editing ? Outcome.UPDATED : Outcome.CREATED, saved.fullName()));
            } catch (EmployeeValidationException invalid) {
                show(owner, invalid, email, firstName, lastName, role, department, region, manager, error);
            } catch (EmployeeNotFoundException gone) {
                dialog.close();
                done.accept(new Result(Outcome.GONE, ""));
            } catch (EditConflictException conflict) {
                dialog.close();
                done.accept(new Result(Outcome.CONFLICT, existing.fullName()));
            } catch (DataAccessException e) {
                log.error("Saving employee {} failed for administrator {}", editing ? existing.id() : "(new)",
                        adminId, e);
                error.setText(owner.getTranslation("manage.saveFailed"));
                error.setVisible(true);
            }
        });
        dialog.getFooter().add(cancel, save);
        dialog.addClosedListener(event -> dialog.removeFromParent());
        dialog.open();
    }

    /** The outcome and the name of the employee it concerned. */
    record Result(Outcome outcome, String name) implements java.io.Serializable {
    }

    private static TextField textField(Component owner, String labelKey, String testId) {
        TextField field = new TextField(owner.getTranslation(labelKey));
        field.setTestId(testId);
        field.setMaxLength(MAX_LENGTH);
        field.setRequiredIndicatorVisible(true);
        return field;
    }

    private static void show(Component owner, EmployeeValidationException invalid, TextField email,
            TextField firstName, TextField lastName, Select<Role> role, Select<Choice> department,
            Select<Choice> region, Select<Choice> manager, Div error) {
        boolean missing = false;
        if (invalid.has(Problem.EMAIL_EXISTS)) {
            fail(email, owner.getTranslation("manage.error.EMAIL_EXISTS"));
        }
        if (invalid.has(Problem.EMAIL_INVALID)) {
            boolean blank = email.getValue() == null || email.getValue().isBlank();
            fail(email, owner.getTranslation(blank ? "manage.error.required" : "manage.error.EMAIL_INVALID"));
            missing |= blank;
        }
        if (invalid.has(Problem.FIRST_NAME_REQUIRED)) {
            fail(firstName, owner.getTranslation("manage.error.required"));
            missing = true;
        }
        if (invalid.has(Problem.LAST_NAME_REQUIRED)) {
            fail(lastName, owner.getTranslation("manage.error.required"));
            missing = true;
        }
        if (invalid.has(Problem.ROLE_REQUIRED)) {
            fail(role, owner.getTranslation("manage.error.required"));
            missing = true;
        }
        if (invalid.has(Problem.DEPARTMENT_REQUIRED) || invalid.has(Problem.DEPARTMENT_UNKNOWN)) {
            fail(department, owner.getTranslation("manage.error.required"));
            missing = true;
        }
        if (invalid.has(Problem.REGION_REQUIRED) || invalid.has(Problem.REGION_UNKNOWN)) {
            fail(region, owner.getTranslation("manage.error.REGION_REQUIRED"));
            missing = true;
        }
        if (invalid.has(Problem.MANAGER_IS_SELF)) {
            fail(manager, owner.getTranslation("manage.error.MANAGER_IS_SELF"));
        }
        if (invalid.has(Problem.MANAGER_CYCLE)) {
            fail(manager, owner.getTranslation("manage.error.MANAGER_CYCLE"));
        }
        if (invalid.has(Problem.MANAGER_UNKNOWN)) {
            fail(manager, owner.getTranslation("manage.error.MANAGER_UNKNOWN"));
        }
        if (invalid.has(Problem.LAST_ADMIN)) {
            fail(role, owner.getTranslation("manage.error.LAST_ADMIN"));
        }
        if (missing) {
            error.setText(owner.getTranslation("manage.error.requiredAll"));
            error.setVisible(true);
        } else if (invalid.has(Problem.TOO_LONG)) {
            error.setText(owner.getTranslation("manage.error.TOO_LONG"));
            error.setVisible(true);
        }
    }

    private static void fail(Component field, String message) {
        var validation = (com.vaadin.flow.component.shared.HasValidationProperties) field;
        validation.setErrorMessage(message);
        validation.setInvalid(true);
    }
}
