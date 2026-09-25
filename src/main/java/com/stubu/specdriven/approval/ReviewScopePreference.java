package com.stubu.specdriven.approval;

import com.vaadin.flow.server.VaadinSession;

/**
 * The scope a manager chose in this session, so the Approvals and Employees pages agree and the choice survives
 * navigating between pages. The default is the direct reports.
 */
public final class ReviewScopePreference {

    private static final String ATTRIBUTE = "review.scope";

    private ReviewScopePreference() {
    }

    public static ReviewScope get() {
        VaadinSession session = VaadinSession.getCurrent();
        Object stored = session == null ? null : session.getAttribute(ATTRIBUTE);
        return stored instanceof ReviewScope scope ? scope : ReviewScope.DIRECT_REPORTS;
    }

    public static void set(ReviewScope scope) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(ATTRIBUTE, scope);
        }
    }
}
