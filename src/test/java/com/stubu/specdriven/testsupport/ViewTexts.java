package com.stubu.specdriven.testsupport;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.dom.Element;

/** The text a user sees on a page, for assertions in browserless tests. */
public final class ViewTexts {

    private ViewTexts() {
    }

    /** The visible text of a component tree; parts that are hidden are left out. */
    public static String text(Component component) {
        return normalize(text(component.getElement()));
    }

    public static String text(Element element) {
        if (element.isTextNode()) {
            return element.getText();
        }
        if (!element.isVisible()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        element.getChildren().forEach(child -> text.append(' ').append(text(child)));
        return text.toString();
    }

    /** The time format separates the time from AM/PM with a narrow no-break space; both kinds become a normal space. */
    public static String normalize(String text) {
        // Runs of white space count as one, as they do on the page.
        return text.replace('\u202f', ' ').replace('\u00a0', ' ').replaceAll(" {2,}", " ");
    }
}
