package com.stubu.specdriven.timetracking;

/** The employee has no open work period. */
public class NotCheckedInException extends RuntimeException {

    public NotCheckedInException() {
        super("Not checked in");
    }
}
