package com.stubu.specdriven.approval;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/** What happened to a timesheet, one line per step: when, what and by whom, from the audit log. */
public class TimesheetHistory extends Div {

    public TimesheetHistory() {
        addClassName("timesheet-history");
        setTestId("history");
    }

    public void show(List<HistoryEntry> history, ZoneId zone) {
        removeAll();
        setVisible(!history.isEmpty());
        if (history.isEmpty()) {
            return;
        }
        Locale locale = getLocale();
        H3 title = new H3(getTranslation("history.title"));
        Div lines = new Div();
        lines.getElement().setAttribute("role", "list");
        DateTimeFormatter format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
                .withZone(zone);
        for (HistoryEntry entry : history) {
            String who = entry.actorName() == null ? getTranslation("history.unknown") : entry.actorName();
            String what = getTranslation("history.action." + entry.action().name(), who);
            Span when = new Span(format.format(entry.at()));
            when.addClassName("history-when");
            Div line = new Div(when, new Span(what));
            if (entry.reason() != null && !entry.reason().isBlank()) {
                Span reason = new Span(getTranslation("history.reason", entry.reason()));
                reason.addClassName("history-reason");
                line.add(reason);
            }
            line.addClassName("history-line");
            line.getElement().setAttribute("role", "listitem");
            lines.add(line);
        }
        add(title, lines);
    }
}
