package com.stubu.specdriven.approval;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.function.SerializableConsumer;
import java.util.List;

/**
 * Lets a manager switch between "Direct reports only" and "All department employees". The current choice is
 * always visible as the selected option; each option shows how many employees it covers, and an option that
 * cannot be used is disabled with an explanation.
 */
public class ScopeSwitcher extends Div {

    private final RadioButtonGroup<ReviewScope> choice = new RadioButtonGroup<>();
    private final Span note = new Span();
    private SerializableConsumer<ReviewScope> listener = scope -> {
    };
    private boolean updating;

    public ScopeSwitcher() {
        addClassName("scope-switcher");
        choice.setTestId("scope");
        choice.addClassName("scope-choice");
        choice.setItems(ReviewScope.values());
        choice.setValue(ReviewScope.DIRECT_REPORTS);
        choice.addValueChangeListener(event -> {
            if (!updating && event.getValue() != null) {
                listener.accept(event.getValue());
            }
        });
        note.addClassName("scope-note");
        note.setTestId("scope-note");
        add(choice, note);
    }

    /** Called when the user picks another scope. */
    public void addScopeListener(SerializableConsumer<ReviewScope> listener) {
        this.listener = listener;
    }

    public ReviewScope getScope() {
        return choice.getValue();
    }

    /** Shows the options and the selected scope, without calling the listener. */
    public void update(ScopeOptions options, ReviewScope selected) {
        updating = true;
        choice.setLabel(getTranslation("scope.label"));
        choice.setItemLabelGenerator(scope -> switch (scope) {
            case DIRECT_REPORTS -> getTranslation("scope.DIRECT_REPORTS", options.directReports());
            case DEPARTMENT -> getTranslation("scope.DEPARTMENT", options.departmentEmployees());
        });
        choice.setItemEnabledProvider(scope -> scope != ReviewScope.DEPARTMENT || options.departmentAvailable());
        choice.setValue(selected);
        updating = false;

        List<String> notes = new java.util.ArrayList<>();
        if (options.directReports() == 0) {
            notes.add(getTranslation("scope.noReports"));
        }
        if (!options.departmentAvailable()) {
            notes.add(getTranslation("scope.noDepartment"));
        }
        note.setText(String.join(" ", notes));
        note.setVisible(!notes.isEmpty());
    }
}
