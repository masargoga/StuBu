package com.stubu.specdriven.base;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * The line at the top of a page that says what just happened: a confirmation ("Timesheet approved.") or an error.
 * Confirmations are announced politely to screen readers, errors immediately.
 */
public class MessageBox extends Div {

    public MessageBox() {
        addClassName("time-message");
        setVisible(false);
    }

    /**
     * Shows the message, or hides the box when there is none.
     *
     * @param text  the translated message, or {@code null} for none
     * @param error whether it reports a problem
     */
    public void show(String text, boolean error) {
        if (text == null) {
            setVisible(false);
            return;
        }
        var icon = (error ? VaadinIcon.WARNING : VaadinIcon.CHECK_CIRCLE).create();
        icon.getElement().setAttribute("aria-hidden", "true");
        removeAll();
        add(icon, new Span(text));
        setClassName("time-message-error", error);
        getElement().setAttribute("role", error ? "alert" : "status");
        setVisible(true);
    }
}
