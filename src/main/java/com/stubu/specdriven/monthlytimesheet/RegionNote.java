package com.stubu.specdriven.monthlytimesheet;

import com.vaadin.flow.component.html.Span;

/**
 * The note "Public holidays: Germany" above a month: it says whose region's holidays are marked, which matters when a
 * manager or administrator looks at the timesheet of an employee who works in another region (UC-017).
 */
public class RegionNote extends Span {

    public RegionNote() {
        addClassName("holiday-region-note");
        setTestId("holiday-region-note");
    }

    /** Shows the region of the timesheet, or nothing when the region is not known. */
    public void show(MonthlyTimesheet sheet) {
        boolean known = sheet != null && sheet.regionName() != null;
        setText(known ? getTranslation("timesheet.holidayRegion", sheet.regionName()) : "");
        setVisible(known);
    }
}
