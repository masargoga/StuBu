# UC-003: View Daily Timesheet

---

**Goal:** As an employee, I want to view my current day's work timeline and total hours so that I can verify my tracked time.

**Status:** Pending
**Date:** 2024-01-15

---

## Actors

- **Primary actor:** Employee (authenticated)

---

## Preconditions

- User is authenticated and has the EMPLOYEE role
- At least one TimeEntry exists for today, OR no entries exist yet (show empty timeline)

---

## Trigger

Employee navigates to the "Time Tracking" or "Today" view.

---

## Main Flow

1. System queries all TimeEntries for the employee for today's date.
2. For each TimeEntry (ordered by check-in time):
   - Display check-in time, check-out time (if completed), and duration.
   - Show status: "In Progress" (if isActive = true) or "Completed" (if isActive = false).
3. System calculates total worked hours: sum of all durations for completed entries + elapsed time for active entry (if any).
4. System displays total worked hours on the dashboard (e.g., "Total hours today: 7h 45m").
5. System displays the current time and, if an entry is active, elapsed time since check-in.
6. System shows "Check-In" and "Check-Out" buttons (with appropriate enable/disable state).
7. System displays a link to edit/correct entries.

---

## Alternative Flows

### AF-1: No Time Entries Today

**Branches from:** Main Flow step 1
**Condition:** No TimeEntries exist for today

1. System displays an empty timeline.
2. System displays "Total hours today: 0h 0m".
3. System displays "Check-In" button enabled and "Check-Out" button disabled.
4. System displays a message: "No time entries recorded yet. Click 'Check In' to start."
5. Use case continues normally.

### AF-2: Only Check-Ins, No Check-Outs

**Branches from:** Main Flow step 3
**Condition:** One or more active TimeEntries exist

1. System calculates total hours from completed entries only.
2. System adds elapsed time for the active entry (current time - check-in time).
3. System displays: "Total hours today: [completed] + [elapsed on active entry]" or similar notation.
4. Use case continues.

### AF-3: Database Error Loading Entries

**Branches from:** Main Flow step 1
**Condition:** Database query fails

1. System displays an error message: "Unable to load time entries. Please try again."
2. "Retry" button is displayed.
3. Use case ends; user can retry.

---

## Postconditions

- **On success:** 
  - Timeline is displayed with all entries for today
  - Total worked hours are calculated and displayed
  - Buttons and edit links are shown

- **On failure:** 
  - Error message is displayed
  - No timeline or totals are shown
  - User can retry or navigate away

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only entries for today (current date) are displayed |
| BR-02 | Entries are displayed in chronological order (earliest check-in first) |
| BR-03 | Total hours include all completed entries plus elapsed time for active entry |
| BR-04 | Public holidays do not affect display or calculation (informational only) |

---

## Tests

- [ ] Main Flow covered (steps 1–7)
- [ ] AF-1 (No Entries) covered
- [ ] AF-2 (Active Entries) covered
- [ ] AF-3 (Database Error) covered
- [ ] BR-01–BR-04 covered

---

## UI Surface

- **Time Tracking Dashboard / Today View:** Displays daily timeline and summary.
  - Timeline list showing all time entries with check-in, check-out, duration, and status.
  - Total worked hours summary (e.g., "7h 45m").
  - Current time display.
  - Check-In / Check-Out buttons.
  - Edit link for each entry.

| Page | Access |
|------|--------|
| Today's Time Tracking | Authenticated Employee |
