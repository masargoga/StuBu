package com.stubu.specdriven.monthlytimesheet;

/** Why a timesheet cannot be submitted (yet). */
public enum SubmitBlocker {
    /** It is already submitted or approved: only a draft or a rejected timesheet can be submitted. */
    NOT_DRAFT,
    /** The month is not over yet. */
    MONTH_NOT_ENDED,
    /** The month has no work periods. */
    EMPTY,
    /** A check-in of the month has no check-out yet. */
    OPEN_ENTRY
}
