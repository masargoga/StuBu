package com.stubu.specdriven.base;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/** "Unable to load data. Please try again." with a Retry button; shown instead of the content when loading failed. */
public class LoadErrorBox extends Div {

    private final Span message = new Span();
    private final Button retry = new Button();

    /** @param onRetry what the Retry button does, usually reloading the page's data */
    public LoadErrorBox(Runnable onRetry) {
        var icon = VaadinIcon.WARNING.create();
        icon.getElement().setAttribute("aria-hidden", "true");
        retry.setTestId("retry");
        retry.addThemeVariants(ButtonVariant.PRIMARY);
        retry.addClickListener(event -> onRetry.run());
        add(icon, message, retry);
        addClassNames("time-message", "time-message-error", "time-load-error");
        getElement().setAttribute("role", "alert");
        setVisible(false);
    }

    /** Shows or hides the box; the texts are translated by the page, so they follow its language. */
    public void update(boolean failed, String messageText, String retryText) {
        message.setText(messageText);
        retry.setText(retryText);
        setVisible(failed);
    }
}
