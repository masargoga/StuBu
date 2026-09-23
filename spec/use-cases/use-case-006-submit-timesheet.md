# UC-006: Submit Timesheet for Approval

---

**Goal:** As an employee, I want to submit my completed monthly timesheet for manager approval so that the approval workflow can proceed.

**Status:** Pending
**Date:** 2024-01-15

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

- [ ] Main Flow covered (steps 1–10)
- [ ] AF-1 (Already Submitted) covered
- [ ] AF-2 (No Entries) covered
- [ ] AF-3 (Cancel) covered
- [ ] AF-4 (Database Error) covered
- [ ] BR-01–BR-05 covered

---

## UI Surface

- **Monthly Timesheet View:** Shows timesheet with status badge and "Submit Timesheet" button (if DRAFT).
- **Confirmation Dialog:** Clear message warning that changes cannot be made after submission.
- **Success Message:** "Timesheet submitted successfully. Awaiting manager approval."
- **Status Display:** "Submitted on [date]" shown after successful submission.

| Page | Access |
|------|--------|
| Monthly Timesheet | Authenticated Employee |
