# UC-009: Manager Views Employee Timesheet and Timeline

---

**Goal:** As a manager, I want to review an employee's complete timesheet and daily timeline so that I can make informed approval decisions.

**Status:** Implemented
**Date:** 2024-01-15

> A use case cannot be marked as **Implemented** unless all criteria in the use case implementation workflow are fulfilled.

> **Revision:** Managers and administrators get an "Employees" page (`/employees`) listing the employees they may look at, with a search by name or email address; "View timesheet" opens `/employees/timesheet/{employeeId}?month=YYYY-MM` for the current month (or the month in the address). The month is chosen with the previous/next buttons or the selector of the last 24 months, like on the employee's own page. The page shows the employee's name and role, the status, the same read-only timeline as the review page with the daily and monthly totals, and a "day details" button on every day with work periods that opens that day's timeline in a dialog. A submitted timesheet offers "Review and decide", which leads to the review page of UC-007. Who may be looked at is decided by the same `ReviewerAuthorization` as for reviewing (direct reports and the own department for managers, everybody for administrators, never oneself). Unlike the employee's own page, looking at a month never creates a timesheet: without one the page says "No timesheet found for [Employee] in [Month]" and the month can be changed (AF-2). BR-04 is realized as a history section under the timesheet with the submission, resubmission, approval and rejection from the audit log (who, when, reason); corrections of single time entries are not listed there. The default list shows direct reports; the switch to the whole department is UC-010.

---

## Actors

- **Primary actor:** Manager (authenticated with MANAGER role)

---

## Preconditions

- User is authenticated and has the MANAGER role
- Employee is a direct report of the manager or manager has department-wide viewing permissions
- Timesheet exists for the selected employee and month

---

## Trigger

Manager navigates to "Pending Approvals" or an employee's profile and clicks to view their timesheet for a specific month.

---

## Main Flow

1. System displays a list of manager's direct reports (or all department employees if applicable).
2. Manager selects an employee.
3. Manager selects a month and year to review.
4. System retrieves the Timesheet for the employee for that month.
5. System displays the timesheet with:
   - Employee name and role
   - Month and year
   - Timesheet status
   - Calendar or table view of all days with time entries
   - Daily totals for each day
   - Monthly total hours
6. Manager reviews entries and totals.
7. Manager can click on individual days or entries to view more details (timeline).
8. System displays daily timeline for selected day, showing:
   - All TimeEntries for that day (check-in, check-out, duration)
   - Total hours for that day
9. Manager navigates back to monthly view.
10. Manager makes approval/rejection decision (see UC-007).

---

## Alternative Flows

### AF-1: Employee Not a Direct Report

**Branches from:** Main Flow step 2
**Condition:** Manager is not the employee's manager and manager role does not allow viewing all employees

1. System displays error: "You do not have permission to view this employee's timesheet."
2. User is redirected to available employees.
3. Use case ends.

### AF-2: No Timesheet for Month

**Branches from:** Main Flow step 4
**Condition:** No Timesheet exists for the selected employee/month

1. System displays message: "No timesheet found for [Employee] in [Month/Year]."
2. Manager can select a different month or employee.
3. Use case ends without viewing timesheet.

### AF-3: No Time Entries in Timesheet

**Branches from:** Main Flow step 5
**Condition:** Timesheet exists but has no TimeEntries

1. System displays empty timesheet: "No time entries recorded for [Month/Year]."
2. Monthly total is 0h 0m.
3. Manager can still approve or reject (depending on business policy).
4. Use case continues.

### AF-4: Database Error Loading Timesheet

**Branches from:** Main Flow step 4
**Condition:** Database query fails

1. System displays error: "Unable to load timesheet. Please try again."
2. "Retry" button is shown.
3. Use case ends; manager can retry or navigate away.

---

## Postconditions

- **On success:** 
  - Manager can see complete timesheet and timeline for the employee
  - Manager can drill down to daily details
  - Manager can proceed to approval/rejection decision

- **On failure:** 
  - Error message is displayed
  - Manager cannot proceed to approval
  - Manager can retry or navigate away

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Manager can only view timesheets for their direct reports, unless granted department-wide access |
| BR-02 | Manager can view timesheets in any status (DRAFT, SUBMITTED, APPROVED, REJECTED) |
| BR-03 | Manager views are read-only; editing is performed through approval/rejection workflow (UC-007) |
| BR-04 | View includes full audit trail visibility (all edits and corrections) if available |

---

## Tests

- [x] Main Flow covered (steps 1–10)
- [x] AF-1 (Not Direct Report) covered
- [x] AF-2 (No Timesheet) covered
- [x] AF-3 (No Entries) covered
- [x] AF-4 (Database Error) covered
- [x] BR-01–BR-04 covered

---

## UI Surface

- **Employee List / Search:** Manager can select employee from list or search.
- **Month Picker:** Dropdown to select month/year for timesheet view.
- **Timesheet View:** Calendar or table showing all days and time entries with totals.
- **Daily Details:** Clickable day or entry to expand and view full timeline.
- **Status Badge:** Shows timesheet status (Draft, Submitted, Approved, Rejected).

| Page | Access |
|------|--------|
| Employee Timesheet Review | Authenticated Manager |
