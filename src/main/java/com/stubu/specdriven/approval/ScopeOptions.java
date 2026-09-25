package com.stubu.specdriven.approval;

/**
 * What a reviewer can choose from in the scope switcher.
 *
 * @param directReports       how many employees report to the reviewer
 * @param departmentEmployees how many employees the reviewer may look at in their department (the direct reports
 *                            included)
 * @param departmentAvailable whether the reviewer's account has a department, so that the department scope works
 */
public record ScopeOptions(int directReports, int departmentEmployees, boolean departmentAvailable) {
}
