# UC-005: View Monthly Timesheet

---

**Goal:** As an employee, I want to view my timesheet for the current or previous month so that I can verify all working hours before submission.

**Status:** Pending
**Date:** 2024-01-15

---

## Actors

- **Primary actor:** Employee (authenticated)

---

## Preconditions

- User is authenticated and has the EMPLOYEE role
- At least one TimeEntry exists for the selected month, OR the month is empty (show empty timesheet)
- A Timesheet record exists or will be auto-created for the selected month

---

## Trigger

Employee navigates to the "Timesheet" or "Monthly" view and selects a month to view.

---

## Main Flow

1. System displays a month/year picker (default to current month).
2. Employee selects a month and year (or defaults to current).
3. System queries the Timesheet record for the employee, month, and year.
   - If no Timesheet exists, system creates one with status DRAFT.
4. System retrieves all TimeEntries for the employee in that month.
5. System groups TimeEntries by date.
6. System displays a calendar view or table showing:
   - Each day with its TimeEntries listed
   - Daily total hours for each day
   - Days with no entries (blank or marked as "No entry")
   - Public holidays highlighted (if any fall in the month)
7. System calculates and displays total hours for the month.
8. System displays the Timesheet status:
   - DRAFT: "Not submitted" with a Submit button
   - SUBMITTED: "Awaiting approval" with no edit/submit buttons
   - APPROVED: "Approved on [date] by [manager]" with read-only display
   - REJECTED: "Rejected on [date] with reason: [reason]" with an Edit & Resubmit button
9. System displays a link to edit/correct daily entries (if DRAFT status).

---

## Alternative Flows

### AF-1: No Time Entries in Month

**Branches from:** Main Flow step 4
**Condition:** No TimeEntries exist for the selected month

1. System displays an empty timesheet.
2. System displays: "No time entries recorded for [Month/Year]."
3. System displays "Total hours: 0h 0m".
4. If status is DRAFT, a Submit button is shown (employee can submit an empty timesheet if allowed, or system may prevent submission).
5. Use case continues.

### AF-2: Navigate to Different Month

**Branches from:** Main Flow step 2
**Condition:** Employee selects a different month

1. System queries Timesheet and TimeEntries for the new month.
2. System refreshes the display.
3. Returns to Main Flow step 5.

### AF-3: Database Error Loading Timesheet

**Branches from:** Main Flow step 3
**Condition:** Database query or creation fails

1. System displays error: "Unable to load timesheet. Please try again."
2. "Retry" button is shown.
3. Use case ends; user can retry or navigate away.

### AF-4: Calendar Display Preferences

**Branches from:** Main Flow step 6
**Condition:** Employee prefers table view instead of calendar

1. System may display TimeEntries in table format (date, check-in, check-out, duration, total).
2. Use case continues normally.

---

## Postconditions

- **On success:** 
  - Timesheet is displayed with all entries for the month
  - Total monthly hours are calculated and displayed
  - Timesheet status is shown
  - Edit and submit buttons are available if status is DRAFT

- **On failure:** 
  - Error message is displayed
  - User can retry or navigate away

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Timesheet record is auto-created on first access for a month (if not exists) |
| BR-02 | Only entries in the selected month are displayed |
| BR-03 | Total hours are sum of all daily totals for the month |
| BR-04 | Timesheet status determines available actions (edit, submit, view-only) |
| BR-05 | Public holidays are shown for reference but do not affect totals |
| BR-06 | Employees can view current and previous months; future months are not displayed |

---

## Tests

- [ ] Main Flow covered (steps 1–9)
- [ ] AF-1 (No Entries) covered
- [ ] AF-2 (Navigate Month) covered
- [ ] AF-3 (Database Error) covered
- [ ] AF-4 (Table View) covered
- [ ] BR-01–BR-06 covered

---

## UI Surface

- **Timesheet View:** Calendar or table layout showing all days in the month with time entries and daily totals.
- **Month Picker:** Dropdown or navigation arrows to select month/year.
- **Status Badge:** Displays current timesheet status (Draft, Submitted, Approved, Rejected).
- **Total Summary:** Shows total hours for the month prominently.
- **Action Buttons:** Submit (if DRAFT), Edit (if DRAFT), Resubmit (if REJECTED).
- **Entry Details:** Clickable entries to view or edit individual time records.

| Page | Access |
|------|--------|
| Monthly Timesheet | Authenticated Employee |
