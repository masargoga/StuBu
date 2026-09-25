package com.stubu.specdriven.base;

import com.vaadin.flow.component.html.Span;

/**
 * Cells of the tables that turn into cards on narrow screens (the "approval-row" pattern in styles.css): on a narrow
 * screen the label is shown in front of the value.
 */
public final class TableCells {

    private TableCells() {
    }

    /**
     * A table cell.
     *
     * @param role  the ARIA role: {@code cell} or {@code columnheader}
     * @param text  the value; {@code null} shows nothing
     * @param label the column name shown in front of the value on narrow screens, or {@code null}
     */
    public static Span cell(String role, String text, String label) {
        Span cell = new Span(text == null ? "" : text);
        cell.addClassName("approval-cell");
        cell.getElement().setAttribute("role", role);
        if (label != null) {
            cell.getElement().setAttribute("data-label", label);
        }
        return cell;
    }
}
