package com.stubu.specdriven.base;

import com.vaadin.flow.component.html.Image;

/**
 * The icon mark of the application: a clock face with a progress arc on a green tile, the same picture as the browser
 * tab icon. It is decoration next to the application name, so it has no alternative text of its own.
 */
public final class AppLogo {

    /** The file in {@code META-INF/resources}; also used as the favicon. */
    public static final String PATH = "icons/stubu-mark.svg";

    private AppLogo() {
    }

    /** The mark as an image; its size is set by the {@code app-logo} styles. */
    public static Image create() {
        Image logo = new Image(PATH, "");
        logo.addClassName("app-logo");
        logo.getElement().setAttribute("aria-hidden", "true");
        return logo;
    }
}
