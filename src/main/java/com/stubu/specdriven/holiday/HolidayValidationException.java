package com.stubu.specdriven.holiday;

import java.time.LocalDate;
import java.util.Set;

/** The holiday cannot be saved; every problem found is listed. */
public class HolidayValidationException extends RuntimeException {

    public enum Problem {
        DATE_INVALID, DATE_TAKEN, NAME_REQUIRED, NAME_TOO_LONG
    }

    private final Set<Problem> problems;
    private final LocalDate date;

    public HolidayValidationException(Set<Problem> problems, LocalDate date) {
        super("Invalid public holiday: " + problems);
        this.problems = Set.copyOf(problems);
        this.date = date;
    }

    public Set<Problem> getProblems() {
        return problems;
    }

    public boolean has(Problem problem) {
        return problems.contains(problem);
    }

    /** The date that was entered, e.g. to say which date is already taken. */
    public LocalDate getDate() {
        return date;
    }
}
