package com.stubu.specdriven.employee;

/** There is no employee with that id. */
public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException() {
        super("Employee not found");
    }
}
