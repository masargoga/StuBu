package com.stubu.specdriven.timetracking;

import com.vaadin.flow.component.timepicker.TimePicker;
import java.time.Duration;
import java.time.LocalTime;

/**
 * The look of the time fields of the application (check-in and check-out times): a list of times to choose from, and
 * the ability to type any minute.
 *
 * <p>A time picker only opens its list for steps of 15 minutes or more, and it accepts times that do not line up with
 * the step, so a step of 15 minutes gives the list without limiting what can be typed. The list starts at midnight,
 * which is far from where most working days begin and end; while nothing is chosen yet it therefore opens scrolled to
 * the morning (check-in fields) or the afternoon (check-out fields).
 */
public final class TimeFields {

    /** The times offered in the list. */
    public static final Duration STEP = Duration.ofMinutes(15);

    /** Where the list of a check-in field opens while the field is empty. */
    public static final LocalTime MORNING = LocalTime.of(8, 0);

    /** Where the list of a check-out field opens while the field is empty. */
    public static final LocalTime AFTERNOON = LocalTime.of(16, 0);

    /**
     * The list is scrolled until the wanted time is this many steps above the bottom edge, so that it is not right at the
     * edge and the times just before it are visible too (10 steps of 15 minutes are two and a half hours).
     */
    static final int STEPS_BELOW = 10;

    private TimeFields() {
    }

    /**
     * Gives the field the list of times and makes the list open scrolled to the given time while the field is empty.
     * The scrolling uses an internal method of the component; if a later version of it is not there any more the list
     * simply opens at the top, as it would without this.
     *
     * @param opensAt {@link #MORNING} for a check-in field, {@link #AFTERNOON} for a check-out field
     */
    public static TimePicker configure(TimePicker picker, LocalTime opensAt) {
        picker.setStep(STEP);
        picker.getElement().executeJs("""
                const target = $0;
                this.addEventListener('opened-changed', () => {
                  if (!this.opened || this.value || !this._scrollIntoView) {
                    return;
                  }
                  const index = Math.round(target / this.step) + $1;
                  // The list is drawn after the event, so wait for it.
                  requestAnimationFrame(() => requestAnimationFrame(() => {
                    try {
                      this._scrollIntoView(index);
                    } catch (e) {
                      // the list stays where it is
                    }
                  }));
                });""", opensAt.toSecondOfDay(), STEPS_BELOW);
        return picker;
    }
}
