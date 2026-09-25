package com.stubu.specdriven.approval;

/** The rejection reason is missing or too long. */
public class InvalidReasonException extends RuntimeException {

    public enum Problem {
        REQUIRED, TOO_LONG
    }

    private final Problem problem;

    public InvalidReasonException(Problem problem) {
        super("Invalid rejection reason: " + problem);
        this.problem = problem;
    }

    public Problem getProblem() {
        return problem;
    }
}
