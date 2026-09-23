# UC-004: Correct Historical Time Entries

---

**Goal:** As an employee, I want to correct or delete previous time entries so that my records are accurate.

**Status:** Pending
**Date:** 2024-01-15

---

## Actors

- **Primary actor:** Employee (authenticated)

---

## Preconditions

- User is authenticated and has the EMPLOYEE role
- TimeEntry exists that the employee owns
- The TimeEntry's parent Timesheet (if any) is in DRAFT status (not yet submitted)

---

## Trigger

Employee clicks "Edit" or "Delete" on a time entry in the timeline or daily view.

---

## Main Flow

### Edit Flow

1. Employee clicks "Edit" on a time entry.
2. System displays an edit form with:
   - Date (read-only, cannot change the date of an entry)
   - Check-in time (editable)
   - Check-out time (editable)
   - Notes/reason for correction (optional text field)
3. Employee updates check-in and/or check-out times.
4. Employee optionally enters a reason for the correction.
5. Employee clicks "Save".
6. System validates:
   - Check-out time must be after check-in time (or null if entry is still active)
   - Times must be today or earlier (no future dates)
7. System updates the TimeEntry in the database.
8. System creates an audit log entry with action UPDATE, capturing old and new values and the reason.
9. System recalculates day/month totals if needed.
10. System displays success message: "Time entry updated."
11. System returns to the timeline view.

### Delete Flow

12. Employee clicks "Delete" on a time entry.
13. System displays a confirmation dialog: "Are you sure you want to delete this time entry? This cannot be undone."
14. Employee confirms deletion.
15. System deletes the TimeEntry from the database (soft delete or hard delete based on audit requirements; recommend soft delete with isDeleted flag).
16. System creates an audit log entry with action DELETE, capturing the deleted values and reason (if provided).
17. System recalculates day/month totals.
18. System displays success message: "Time entry deleted."
19. System returns to the timeline view.

---

## Alternative Flows

### AF-1: Validation Fails (Check-Out Before Check-In)

**Branches from:** Main Flow step 6 (Edit validation)
**Condition:** Check-out time is before check-in time

1. System displays error: "Check-out time must be after check-in time."
2. User remains in the edit form.
3. User corrects the times and resubmits.
4. Returns to Main Flow step 6.

### AF-2: Validation Fails (Future Date)

**Branches from:** Main Flow step 6 (Edit validation)
**Condition:** Check-in time is in the future

1. System displays error: "You cannot log time in the future."
2. User remains in the edit form.
3. User corrects the times and resubmits.
4. Returns to Main Flow step 6.

### AF-3: Timesheet Already Submitted

**Branches from:** Preconditions
**Condition:** The TimeEntry belongs to a Timesheet with status SUBMITTED, APPROVED, or REJECTED

1. System disables "Edit" and "Delete" buttons for that entry.
2. System displays a message: "This entry cannot be edited because the timesheet has been submitted. Wait for approval or rejection before making changes."
3. Use case ends.

### AF-4: Database Error

**Branches from:** Main Flow step 7 or 15
**Condition:** Database operation fails

1. System displays error: "Unable to save changes. Please try again."
2. User remains in the edit form (for edit) or sees a retry option (for delete).
3. User can retry.
4. Use case ends without state change.

---

## Postconditions

- **On success (Edit):** 
  - TimeEntry is updated with new check-in/check-out times
  - Audit log captures old and new values
  - Timeline is refreshed to show updated entry
  - Day/month totals are recalculated

- **On success (Delete):** 
  - TimeEntry is removed from timeline
  - Audit log captures deletion
  - Day/month totals are recalculated

- **On failure:** 
  - No changes are made to TimeEntry
  - Error message is displayed
  - User can retry or cancel

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Employees can only edit/delete their own time entries |
| BR-02 | Corrections are only allowed if the parent Timesheet is in DRAFT status |
| BR-03 | Check-out time must be after check-in time (or null if active) |
| BR-04 | Times cannot be in the future |
| BR-05 | All corrections must be logged to AuditLog with old/new values and reason |
| BR-06 | Managers and admins can also edit/delete employee entries (see separate use case) |

---

## Tests

- [ ] Main Flow Edit covered (steps 1–11)
- [ ] Main Flow Delete covered (steps 12–19)
- [ ] AF-1 (Validation: Check-Out Before Check-In) covered
- [ ] AF-2 (Validation: Future Date) covered
- [ ] AF-3 (Timesheet Submitted) covered
- [ ] AF-4 (Database Error) covered
- [ ] BR-01–BR-06 covered

---

## UI Surface

- **Timeline / Daily View:** Each time entry has "Edit" and "Delete" actions (disabled if timesheet is submitted).
- **Edit Form:** Modal or page showing date (read-only), check-in time, check-out time, and reason text field.
- **Confirmation Dialog:** For delete action, confirming destructive operation.
- **Success / Error Messages:** Clear feedback on edit/delete results.

| Page | Access |
|------|--------|
| Edit Time Entry | Authenticated Employee (own entries only) |
