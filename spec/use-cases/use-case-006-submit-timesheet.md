# UC-006: Submit Timesheet for Approval

---

**Goal:** As an employee, I want to submit my completed monthly timesheet for manager approval so that the approval workflow can proceed.

**Status:** Implemented
**Date:** 2024-01-15

> A use case cannot be marked as **Implemented** unless all criteria in the use case implementation workflow are fulfilled.

> **Revision:** The button lives on the monthly timesheet page (UC-005). A timesheet can be submitted once its month is over in the user's time zone (until then the button is disabled and says from when it becomes available), which is what "all time entries for the month are finalized" means here; a check-in without check-out in the month also blocks the submission. An empty month or an open check-in is reported when the button is pressed (AF-2), without the confirmation dialog. The status change and the audit entry (action SUBMIT, old and new status as JSON) are written in one transaction, and two sessions submitting at the same moment cannot both succeed (optimistic locking on the timesheet). Once submitted, the entries of the month can no longer be corrected or deleted (checked by the time entry service). The manager's notification is an email behind a `NotificationService` interface: `spring-boot-starter-mail` sends it when `spring.mail.host` is configured, otherwise it is only logged. The texts are translated (`stubu.notifications.locale`). The notification is sent after the submission has been stored; if it fails, that is logged and the submission stands, and an employee without a manager can still submit. The manager's own list of waiting timesheets belongs to UC-007.

---

## Actors

- **Primary actor:** Employee (authenticated)
- **Secondary actors:** Manager (receives submission notification)

---

## Preconditions

- User is authenticated and has the EMPLOYEE role
- A Timesheet exists with status DRAFT
- Timesheet contains at least one TimeEntry
- All time entries for the month are finalized (employee has stopped editing)

---

## Trigger

Employee navigates to the Monthly Timesheet view and clicks "Submit Timesheet" button.

---

## Main Flow

1. Employee reviews their complete monthly timesheet.
2. Employee clicks "Submit Timesheet" button.
3. System displays a confirmation dialog: "Submit this timesheet for approval? Once submitted, you cannot make changes until it is approved or rejected."
4. Employee confirms submission.
5. System validates:
   - Timesheet status is DRAFT
   - At least one TimeEntry exists
6. System updates Timesheet:
   - status = SUBMITTED
   - submittedAt = current timestamp
7. System creates an audit log entry: action = SUBMIT, captures employee and timestamp.
8. System creates a notification/task for the employee's manager indicating a timesheet awaits approval.
9. System displays success message: "Timesheet submitted successfully. Awaiting manager approval."
10. System redirects to timesheet view showing status as "Submitted".

---

## Alternative Flows

### AF-1: Timesheet Already Submitted

**Branches from:** Preconditions
**Condition:** Timesheet status is already SUBMITTED, APPROVED, or REJECTED

1. System disables "Submit" button.
2. System displays status: "Submitted on [date]" or "Approved on [date]" or "Rejected on [date] - Reason: [reason]".
3. If status is REJECTED, a "Resubmit" button is shown instead (see UC-007).
4. Use case ends.

### AF-2: No Time Entries

**Branches from:** Main Flow step 5
**Condition:** Timesheet has no TimeEntries

1. System displays error: "Cannot submit an empty timesheet. Add at least one time entry."
2. User is directed to add time entries.
3. Use case ends.

### AF-3: Employee Cancels Submission

**Branches from:** Main Flow step 4
**Condition:** Employee clicks "Cancel" in confirmation dialog

1. Dialog closes.
2. Timesheet remains in DRAFT status.
3. Use case ends without changes.

### AF-4: Database Error During Submission

**Branches from:** Main Flow step 6
**Condition:** Database fails to update Timesheet or create audit log

1. System displays error: "Submission failed. Please try again."
2. Timesheet remains in DRAFT status.
3. User can retry.
4. Use case ends without state change.

---

## Postconditions

- **On success:** 
  - Timesheet status is SUBMITTED
  - Submission timestamp is recorded
  - Audit log entry captures submission
  - Manager receives notification
  - Employee sees "Awaiting Approval" status
  - Edit/Submit buttons are disabled

- **On failure:** 
  - Timesheet remains in DRAFT status
  - Error message is displayed
  - No notification is sent
  - User can retry or navigate away

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Timesheet must be in DRAFT status to submit |
| BR-02 | At least one TimeEntry is required for submission |
| BR-03 | Once submitted, employee cannot edit entries until rejected (BR applies at DB level) |
| BR-04 | Manager is notified (via UI notification or email) when timesheet is submitted |
| BR-05 | Submission is logged to AuditLog with timestamp and employee context |

---

## Tests

- [x] Main Flow covered (steps 1–10)
- [x] AF-1 (Already Submitted) covered
- [x] AF-2 (No Entries) covered
- [x] AF-3 (Cancel) covered
- [x] AF-4 (Database Error) covered
- [x] BR-01–BR-05 covered

---

## UI Surface

- **Monthly Timesheet View:** Shows timesheet with status badge and "Submit Timesheet" button (if DRAFT).
- **Confirmation Dialog:** Clear message warning that changes cannot be made after submission.
- **Success Message:** "Timesheet submitted successfully. Awaiting manager approval."
- **Status Display:** "Submitted on [date]" shown after successful submission.

| Page | Access |
|------|--------|
| Monthly Timesheet | Authenticated Employee |
