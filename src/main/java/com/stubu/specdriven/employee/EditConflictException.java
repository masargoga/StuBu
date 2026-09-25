package com.stubu.specdriven.employee;

/**
 * The record was changed by somebody else after the administrator opened it, so saving would overwrite that change.
 * The administrator has to look at the current data and decide again.
 */
public class EditConflictException extends RuntimeException {

    public EditConflictException() {
        super("The record was changed by somebody else in the meantime");
    }
}
