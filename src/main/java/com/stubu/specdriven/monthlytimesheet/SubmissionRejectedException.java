package com.stubu.specdriven.monthlytimesheet;

/** The timesheet cannot be submitted, for the reason given. */
public class SubmissionRejectedException extends RuntimeException {

    private final SubmitBlocker blocker;

    public SubmissionRejectedException(SubmitBlocker blocker) {
        super("Timesheet cannot be submitted: " + blocker);
        this.blocker = blocker;
    }

    public SubmitBlocker getBlocker() {
        return blocker;
    }
}
