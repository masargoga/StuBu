package com.stubu.specdriven.base;

import com.vaadin.flow.component.html.Div;

/** The place in a dialog where a failed save is explained; hidden until there is something to say. */
public class DialogError extends Div {

    public DialogError() {
        addClassName("time-dialog-error");
        getElement().setAttribute("role", "alert");
        setVisible(false);
    }

    /** Shows the message. */
    public void show(String text) {
        setText(text);
        setVisible(true);
    }
}
