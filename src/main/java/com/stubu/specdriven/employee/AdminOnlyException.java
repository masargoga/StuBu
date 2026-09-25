package com.stubu.specdriven.employee;

/** Only active administrators may manage employees and other master data. */
public class AdminOnlyException extends RuntimeException {

    public AdminOnlyException() {
        super("Only administrators may do this");
    }
}
