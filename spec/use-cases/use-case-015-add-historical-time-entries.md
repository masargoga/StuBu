# UC-015: Add Historical Time Entries

---

**Goal:** As an employee, I want to add new working time entries for today and previous days in the timesheet view, so that I can add missing or forgotten working hours before I submit my timesheet.

**Status:** Implemented
**Date:** 2026-09-26

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

> **Revision:** The form is one dialog with the fields Date, Check-in time, Check-out time, Check-out date (follows the date until changed) and "Reason (optional)". The general "Add time entry" button sits next to the month selector; the per-day "Add entry" buttons are part of the timeline rows for every day up to and including today. "Save and add another" keeps the dialog open and shows "Time entry added." inside it; the page behind it is reloaded after every saved entry. In a locked month the general button is disabled, there are no per-day buttons, and the status card says "The entries of this timesheet cannot be edited because it has been submitted." Without a reason the audit entry says "Added afterwards". The two time fields open a list of times in 15-minute steps when the field or its clock icon is clicked, so a time can be chosen with the mouse or by touch; any minute can also be typed (for example 8:03 AM), and the typed minute is what is stored (BR-10).

---

## Actors

- **Primary actor:** Employee. Every signed-in user (employee, manager, administrator) records their own working time, so all of them use this for themselves and only for themselves.

---

## Preconditions

- User is signed in (the EMPLOYEE role, which managers and administrators also hold).
- The timesheet of the month the entry falls in is not submitted or approved: it is a draft (a month without a timesheet record counts as a draft) or it was rejected. This is the same rule as for correcting entries in UC-004.

---

## Trigger

On the timesheet page ("My Timesheet") the employee clicks **"Add time entry"** (next to the month selector, available in the timeline and in the table view), or **"Add entry"** on the row of a day (timeline view; the date of the row is filled in).

---

## Main Flow

1. Employee opens the timesheet page for a month.
2. Employee clicks "Add time entry", or "Add entry" on a day row.
3. System shows the add form with:
   - Date (a day up to and including today; preset when started from a day row)
   - Check-in time (chosen from a list of times in 15-minute steps that opens when the field or its clock icon is clicked, or typed to the minute)
   - Check-out time (the same)
   - Check-out date (the same day by default; the next day for work past midnight)
   - Reason (optional text, kept in the audit log)
   - Buttons "Save", "Save and add another" and "Cancel"
4. Employee enters the date and the times, and optionally a reason.
5. Employee clicks "Save".
6. System validates:
   - Date, check-in time and check-out time are present
   - Check-out is after check-in
   - Neither time is in the future
   - The period does not overlap another work period of the employee (a check-in that is still open counts as running until now)
   - The timesheet of the month of the check-in still allows changes
7. System creates a completed time entry for the employee (seconds are set to zero; the times are stored as absolute instants, the date and times are read in the employee's time zone).
8. System creates an audit log entry with action CREATE, the new values and the reason.
9. System closes the form and reloads the month: the entry, the day's total and break, and the month's totals are updated.
10. System displays success message: "Time entry added."

---

## Alternative Flows

### AF-1: Save and Add Another

**Branches from:** Main Flow step 5
**Condition:** Employee clicks "Save and add another" instead of "Save"

1. System validates and stores the entry as in Main Flow steps 6 to 8.
2. System keeps the form open with the same date and empty times and reason, reloads the month behind it, and displays "Time entry added."
3. Returns to Main Flow step 4.

### AF-2: Missing or Invalid Fields

**Branches from:** Main Flow step 6
**Condition:** A required field is empty, or check-out is not after check-in

1. System shows the error in the form: "Please enter the date and the times." or "Check-out time must be after check-in time."
2. No entry is stored. The form stays open with the entered values.
3. Returns to Main Flow step 4.

### AF-3: Future Date or Time

**Branches from:** Main Flow step 6
**Condition:** The date is after today, or check-in or check-out is later than the current server time

1. System shows the error: "You cannot log time in the future."
2. No entry is stored. The form stays open. (The date picker does not offer days after today.)
3. Returns to Main Flow step 4.

### AF-4: Overlapping Work Period

**Branches from:** Main Flow step 6
**Condition:** The period overlaps another work period of the employee, including a check-in that is still open

1. System shows the error: "This period overlaps a work period you already recorded."
2. No entry is stored. The form stays open.
3. Returns to Main Flow step 4.

### AF-5: Timesheet Locked

**Branches from:** Main Flow steps 2 and 6
**Condition:** The timesheet of the month is submitted or approved, either already when the page is shown or after the form was opened

1. While the page is shown: the add buttons of a locked month are disabled or not offered, and the status card explains: "The entries of this timesheet cannot be edited because it has been submitted."
2. When the timesheet was submitted after the form was opened: System refuses the save, stores nothing, closes the form and shows: "This entry cannot be edited because the timesheet has been submitted. Wait for approval or rejection before making changes."
3. Use case ends.

### AF-6: Database Error

**Branches from:** Main Flow step 7
**Condition:** Storing the entry fails

1. System displays error: "Unable to save changes. Please try again."
2. No entry and no audit entry are stored. The form stays open so the employee can save again.
3. Returns to Main Flow step 5.

### AF-7: Employee Cancels

**Branches from:** Main Flow steps 3 to 5
**Condition:** Employee clicks "Cancel"

1. Form closes without saving.
2. No changes are made.
3. Use case ends.

---

## Postconditions

- **On success:** A completed time entry exists for the employee with the entered check-in and check-out; an audit entry with action CREATE, the new values and the reason exists; the month shows the entry in the timeline and table, and the day and month totals include it. The status of the timesheet is unchanged.
- **On failure:** No time entry and no audit entry are created; the timesheet is unchanged.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Employees add entries only for themselves, never for another employee; the employee always comes from the signed-in user, never from the form |
| BR-02 | The date can be today or any day in the past, with no earlier limit other than the state of the timesheet; days after today cannot be chosen |
| BR-03 | Neither check-in nor check-out may be in the future; check-out must be after check-in; a period may end after midnight (check-out date the next day) as long as it is not in the future |
| BR-04 | A period may not overlap another work period of the employee; a check-in that is still open counts as running until now, so an entry for today cannot cover the time since that check-in |
| BR-05 | The month of the check-in decides whether adding is allowed: a draft (including a month without a timesheet record) or a rejected timesheet allows it, a submitted or approved one does not (same rule as UC-004) |
| BR-06 | Every added entry is written to the audit log with action CREATE, the new values and the optional reason, in the same transaction as the entry |
| BR-07 | There is no maximum number of hours per day or per entry and no automatic blocking because of legal working-time limits (spec.md section 31) |
| BR-08 | Adding entries does not change the status of a timesheet; an entry added to a rejected timesheet stays part of it and is submitted with it (UC-008) |
| BR-09 | The employee's time zone (the browser's) decides which day a period belongs to; the times are stored as absolute instants (spec.md section 8) |
| BR-10 | The time fields offer a list of times in 15-minute steps, opened by clicking the field or its clock icon (a step below 15 minutes would hide the list); the employee can also type any time to the minute, and the time typed is the time stored, whether or not it is a multiple of 15 minutes |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [x] Main Flow covered (steps 1–10), started from "Add time entry" and from a day row
- [x] AF-1 (Save and add another) covered
- [x] AF-2 (Missing or invalid fields) covered
- [x] AF-3 (Future date or time) covered
- [x] AF-4 (Overlapping work period, including an open check-in) covered
- [x] AF-5 (Timesheet locked, before and after opening the form) covered
- [x] AF-6 (Database error) covered
- [x] AF-7 (Cancel) covered
- [x] BR-01–BR-10 covered
- [x] Layout checked at desktop, tablet and phone size

---

## UI Surface

- **Timesheet page:** An "Add time entry" button next to the month selector, available in the timeline view and in the table view. In the timeline view every day row up to and including today also has an "Add entry" button. In a locked month the buttons are disabled and the explanation is shown.
- **Add form:** A modal with the date, check-in time, check-out time, check-out date (same day by default) and an optional reason. Clicking a time field or its clock icon opens a list of times (every 15 minutes) to choose from; times can also be typed. Buttons "Save" (primary), "Save and add another" and "Cancel". Errors are shown at the fields they belong to. Large touch targets; usable at phone, tablet and desktop size.
- **Feedback:** A confirmation message "Time entry added." after saving, and clear error messages as described in the alternative flows.

| Page | Access |
|------|--------|
| Timesheet page with the add form (dialog) | Authenticated (employee, manager, administrator; own time only) |
