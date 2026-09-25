package com.stubu.specdriven.approval;

/** Whose timesheets a manager looks at. */
public enum ReviewScope {
    /** Employees whose manager is the reviewer. */
    DIRECT_REPORTS,
    /** All employees of the reviewer's department. */
    DEPARTMENT
}
