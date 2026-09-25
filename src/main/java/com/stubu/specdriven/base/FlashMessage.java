package com.stubu.specdriven.base;

import com.vaadin.flow.server.VaadinSession;
import java.io.Serializable;

/**
 * A message that is shown once on the next page, e.g. "Timesheet approved." after the review page handed over to
 * the list. It lives in the session until the next page takes it.
 *
 * @param key   the translation key of the message
 * @param error whether it reports a problem
 */
public record FlashMessage(String key, boolean error) implements Serializable {

    private static final String ATTRIBUTE = "flash.message";

    /** Remembers the message for the next page. */
    public static void set(String key, boolean error) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(ATTRIBUTE, new FlashMessage(key, error));
        }
    }

    /** The message waiting for this page, if any; it is shown only once. */
    public static FlashMessage take() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return null;
        }
        FlashMessage message = (FlashMessage) session.getAttribute(ATTRIBUTE);
        session.setAttribute(ATTRIBUTE, null);
        return message;
    }
}
