# UC-007: Review and Approve/Reject Timesheet

---

**Goal:** As a manager, I want to review and approve or reject employee timesheets so that working hours are officially recorded.

**Status:** Pending
**Date:** 2024-01-15

---

## Actors

- **Primary actor:** Manager (authenticated with MANAGER role)
- **Secondary actors:** Employee (receives approval/rejection notification)

---

## Preconditions

- User is authenticated and has the MANAGER role
- Timesheet exists with status SUBMITTED
- Timesheet belongs to the manager's direct report (or manager has permission to review all employees in department)
- Manager has not yet approved or rejected this timesheet

---

## Trigger

Manager navigates to the "Pending Approvals" view and clicks on an employee's submitted timesheet to review.

---

## Main Flow

### Review Flow

1. System displays the employee's submitted timesheet with:
   - Employee name and month/year
   - All time entries grouped by date
   - Daily totals and monthly total hours
   - Timesheet status: "Submitted on [date]"
2. Manager reviews all entries and totals.
3. Manager may scroll or navigate to view timeline details for specific days.

### Approve Flow

4. Manager clicks "Approve Timesheet" button.
5. System displays a confirmation dialog: "Approve this timesheet?"
6. Manager confirms.
7. System updates Timesheet:
   - status = APPROVED
   - approvedAt = current timestamp
   - approvedBy = current manager's employee ID (or captured in audit log)
8. System creates an audit log entry: action = APPROVE, captures manager and timestamp.
9. System removes timesheet from manager's pending approval queue.
10. System creates a notification for the employee: "Your timesheet for [month] was approved."
11. System displays success message: "Timesheet approved."
12. System redirects to pending approvals list or shows updated status.

### Reject Flow

13. Manager clicks "Reject Timesheet" button.
14. System displays a rejection form with:
   - Text field for rejection reason (mandatory)
   - Confirmation checkbox
15. Manager enters a reason (e.g., "Discrepancy in Friday's hours").
16. Manager confirms rejection.
17. System updates Timesheet:
   - status = REJECTED
   - rejectedAt = current timestamp
   - rejectionReason = manager's reason
   - rejectedBy = current manager's employee ID (captured in audit log)
18. System creates an audit log entry: action = REJECT, captures manager, reason, and timestamp.
19. System resets timesheet status to allow employee to edit.
20. System creates a notification for the employee: "Your timesheet for [month] was rejected. Reason: [reason]"
21. System displays success message: "Timesheet rejected."
22. System redirects to pending approvals list.

---

## Alternative Flows

### AF-1: Timesheet Not in SUBMITTED Status

**Branches from:** Preconditions
**Condition:** Timesheet status is DRAFT, APPROVED, or REJECTED

1. System disables Approve/Reject buttons.
2. System displays status message (e.g., "Already approved" or "In draft status").
3. Use case ends.

### AF-2: Employee Not a Direct Report

**Branches from:** Preconditions
**Condition:** Manager is not the employee's manager and manager role does not allow viewing all department employees

1. System displays error: "You do not have permission to review this timesheet."
2. User is redirected to pending approvals list.
3. Use case ends.

### AF-3: Manager Cancels Rejection

**Branches from:** Main Flow step 16 (Reject)
**Condition:** Manager clicks "Cancel" in rejection form

1. Form closes without saving.
2. Timesheet remains in SUBMITTED status.
3. Use case ends.

### AF-4: Manager Cancels Approval

**Branches from:** Main Flow step 6 (Approve)
**Condition:** Manager clicks "Cancel" in confirmation dialog

1. Dialog closes.
2. Timesheet remains in SUBMITTED status.
3. Use case ends.

### AF-5: Database Error During Approval

**Branches from:** Main Flow step 7 (Approve)
**Condition:** Database fails to update Timesheet

1. System displays error: "Approval failed. Please try again."
2. Timesheet remains in SUBMITTED status.
3. User can retry.
4. Use case ends without state change.

### AF-6: Database Error During Rejection

**Branches from:** Main Flow step 17 (Reject)
**Condition:** Database fails to update Timesheet

1. System displays error: "Rejection failed. Please try again."
2. Timesheet remains in SUBMITTED status.
3. User can retry.
4. Use case ends without state change.

---

## Postconditions

- **On approval:** 
  - Timesheet status is APPROVED
  - Approval timestamp is recorded
  - Employee is notified
  - Audit log entry captures approval
  - Timesheet is removed from pending approvals

- **On rejection:** 
  - Timesheet status is REJECTED
  - Rejection timestamp and reason are recorded
  - Timesheet returns to editable state for employee
  - Employee is notified with rejection reason
  - Audit log entry captures rejection and reason

- **On failure:** 
  - Timesheet remains in SUBMITTED status
  - Error message is displayed
  - No notification is sent
  - User can retry

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only SUBMITTED timesheets can be approved or rejected |
| BR-02 | Manager must be the employee's manager or have department-wide viewing permissions |
| BR-03 | Rejection reason is mandatory |
| BR-04 | Rejection returns timesheet to DRAFT status, allowing employee to edit and resubmit |
| BR-05 | Approved timesheets become immutable (no further edits allowed) |
| BR-06 | All approvals/rejections are logged with manager context and reason |

---

## Tests

- [ ] Main Flow Review covered (steps 1–3)
- [ ] Main Flow Approve covered (steps 4–12)
- [ ] Main Flow Reject covered (steps 13–22)
- [ ] AF-1 (Wrong Status) covered
- [ ] AF-2 (Permission Denied) covered
- [ ] AF-3 (Cancel Rejection) covered
- [ ] AF-4 (Cancel Approval) covered
- [ ] AF-5 (Database Error Approve) covered
- [ ] AF-6 (Database Error Reject) covered
- [ ] BR-01–BR-06 covered

---

## UI Surface

- **Pending Approvals List:** Shows submitted timesheets awaiting manager approval, with employee name, month, submission date.
- **Timesheet Review Page:** Displays employee's timesheet with all entries, daily totals, and monthly total.
- **Approve Button:** Prominent button with confirmation dialog.
- **Reject Button:** Opens rejection form with mandatory reason field and confirmation.
- **Success/Error Messages:** Clear feedback on approval/rejection completion.

| Page | Access |
|------|--------|
| Pending Approvals | Authenticated Manager |
| Timesheet Review | Authenticated Manager |
