package com.stubu.specdriven.holiday;

/** There is no public holiday with that id. */
public class HolidayNotFoundException extends RuntimeException {

    public HolidayNotFoundException() {
        super("Public holiday not found");
    }
}
