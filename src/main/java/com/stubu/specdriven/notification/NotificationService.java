package com.stubu.specdriven.notification;

/**
 * Tells people about events in the approval workflow. Domain code depends on this interface only, so the way
 * messages are delivered (today email) can change without touching it.
 */
public interface NotificationService {

    /** A manager has a timesheet waiting for approval. Delivery problems are the implementation's to handle. */
    void timesheetSubmitted(TimesheetSubmittedNotice notice);
}
