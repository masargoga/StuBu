package com.stubu.specdriven.approval;

/** The timesheet does not exist or the user may not review it (deliberately not told apart). */
public class ReviewNotAllowedException extends RuntimeException {

    public ReviewNotAllowedException() {
        super("The timesheet cannot be reviewed by this user");
    }
}
