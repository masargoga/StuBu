# UC-008: Correct and Resubmit Rejected Timesheet

---

**Goal:** As an employee, I want to correct the issues noted in a rejected timesheet and resubmit it for approval.

**Status:** Pending
**Date:** 2024-01-15

---

## Actors

- **Primary actor:** Employee (authenticated)

---

## Preconditions

- User is authenticated and has the EMPLOYEE role
- A Timesheet exists with status REJECTED
- Timesheet includes rejection reason and rejection timestamp
- Employee is viewing the rejected timesheet

---

## Trigger

Employee navigates to the Monthly Timesheet view and sees a rejected timesheet with a rejection reason.

---

## Main Flow

1. System displays the rejected timesheet with:
   - Status: "Rejected on [date]"
   - Rejection reason: "[Manager's reason]"
   - All time entries (now editable)
   - "Edit Entries" button and "Resubmit" button
2. Employee reads the rejection reason.
3. Employee clicks "Edit Entries" or individual entry edit buttons to correct the timesheet based on the rejection reason.
4. Employee corrects the relevant time entries (see UC-004 for editing details).
5. Employee reviews the corrected timesheet.
6. Employee clicks "Resubmit Timesheet" button.
7. System displays a confirmation dialog: "Resubmit this corrected timesheet for approval?"
8. Employee confirms.
9. System validates:
   - Timesheet status is REJECTED
   - At least one TimeEntry exists
10. System updates Timesheet:
    - status = SUBMITTED
    - submittedAt = current timestamp
    - Note: rejectionReason and rejectedAt remain in record for audit trail
11. System creates an audit log entry: action = SUBMIT, captures employee and timestamp (indicates resubmission after rejection).
12. System creates a notification for the manager indicating the previously rejected timesheet has been resubmitted.
13. System displays success message: "Timesheet resubmitted. Awaiting manager approval."
14. System redirects to timesheet view showing status as "Submitted".

---

## Alternative Flows

### AF-1: Timesheet Not Rejected

**Branches from:** Preconditions
**Condition:** Timesheet status is DRAFT, SUBMITTED, or APPROVED

1. System hides "Resubmit" button.
2. System displays current status.
3. Use case ends; different flow applies (UC-006 for DRAFT, view-only for APPROVED).

### AF-2: No Time Entries After Correction

**Branches from:** Main Flow step 9
**Condition:** Employee deleted all time entries during correction

1. System displays error: "Cannot resubmit an empty timesheet. Add at least one time entry."
2. User is directed to add entries.
3. Use case ends.

### AF-3: Employee Cancels Resubmission

**Branches from:** Main Flow step 8
**Condition:** Employee clicks "Cancel" in confirmation dialog

1. Dialog closes.
2. Timesheet remains in REJECTED status.
3. Timesheet is still editable for employee.
4. Use case ends without changes.

### AF-4: Database Error During Resubmission

**Branches from:** Main Flow step 10
**Condition:** Database fails to update Timesheet

1. System displays error: "Resubmission failed. Please try again."
2. Timesheet remains in REJECTED status.
3. User can retry.
4. Use case ends without state change.

---

## Postconditions

- **On success:** 
  - Timesheet status changes from REJECTED to SUBMITTED
  - New submission timestamp is recorded
  - Original rejection information is retained in audit trail
  - Manager is notified of resubmission
  - Employee sees "Awaiting Approval" status
  - Timesheet is locked from further employee edits

- **On failure:** 
  - Timesheet remains in REJECTED status
  - Error message is displayed
  - No notification is sent
  - Timesheet remains editable
  - User can retry

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only REJECTED timesheets can be resubmitted |
| BR-02 | Resubmission requires at least one TimeEntry |
| BR-03 | Original rejection information is retained for audit trail |
| BR-04 | Resubmission is treated as a new submission (updates submittedAt, sends notification) |
| BR-05 | Employee can correct entries multiple times before resubmission |

---

## Tests

- [ ] Main Flow covered (steps 1–14)
- [ ] AF-1 (Not Rejected) covered
- [ ] AF-2 (No Entries) covered
- [ ] AF-3 (Cancel Resubmission) covered
- [ ] AF-4 (Database Error) covered
- [ ] BR-01–BR-05 covered

---

## UI Surface

- **Rejected Timesheet View:** Displays rejection reason prominently and shows "Resubmit" button alongside existing edit options.
- **Edit Entries:** Employees can click individual entries or a bulk "Edit Entries" button to make corrections.
- **Resubmit Button:** Prominent button with confirmation dialog.
- **Success Message:** "Timesheet resubmitted. Awaiting manager approval."
- **Status Display:** "Submitted on [date] (resubmitted after rejection on [original date])" or similar.

| Page | Access |
|------|--------|
| Rejected Timesheet | Authenticated Employee |
