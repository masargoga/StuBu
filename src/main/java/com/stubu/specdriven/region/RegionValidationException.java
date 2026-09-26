package com.stubu.specdriven.region;

import java.util.Set;

/** The region cannot be saved; every problem found is listed. */
public class RegionValidationException extends RuntimeException {

    public enum Problem {
        NAME_REQUIRED, NAME_TOO_LONG, NAME_TAKEN
    }

    private final Set<Problem> problems;

    public RegionValidationException(Set<Problem> problems) {
        super("Invalid region: " + problems);
        this.problems = Set.copyOf(problems);
    }

    public Set<Problem> getProblems() {
        return problems;
    }

    public boolean has(Problem problem) {
        return problems.contains(problem);
    }
}
