package com.stubu.specdriven.timetracking;

/** The work period does not exist (any more) or does not belong to the employee. */
public class EntryNotFoundException extends RuntimeException {

    public EntryNotFoundException() {
        super("Time entry not found");
    }
}
