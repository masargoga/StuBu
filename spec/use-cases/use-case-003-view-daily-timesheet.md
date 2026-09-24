# UC-003: View Daily Timesheet

---

**Goal:** As an employee, I want to view my current day's work timeline and total hours so that I can verify my tracked time.

**Status:** Implemented
**Date:** 2024-01-15

> A use case cannot be marked as **Implemented** unless all criteria in the use case implementation workflow are fulfilled.

> **Revision:** The view is the time tracking panel that UC-002 already provides on the home page; UC-003 completes it. The edit link is shown disabled until UC-004 (Correct Historical Time Entries) provides the editor. The panel refreshes itself while it is open, so the current time, the elapsed time and the totals stay correct without a reload.

---

## Actors

- **Primary actor:** Employee (authenticated)

---

## Preconditions

- User is authenticated and has the EMPLOYEE role
- At least one TimeEntry exists for today, OR no entries exist yet (show empty timeline)

---

## Trigger

Employee navigates to the home page (the "Time Tracking" / "Today" view).

---

## Main Flow

1. System queries all TimeEntries for the employee for today's date.
2. For each TimeEntry (ordered by check-in time):
   - Display check-in time, check-out time (if completed), and duration.
   - Show status: "In Progress" (if the entry is open) or "Completed".
3. System calculates total worked hours: sum of all durations for completed entries + elapsed time for the open entry (if any).
4. System displays total worked hours on the dashboard (e.g., "Total hours today: 7h 45m") and the total break time.
5. System displays the current time and, if an entry is open, elapsed time since check-in.
6. System shows "Check-In" and "Check-Out" buttons (the one that fits the current status is emphasized, see UC-002).
7. System displays an "Edit" control on every entry. Until UC-004 exists it is disabled and explains that editing is not available yet.
8. While the page stays open, System refreshes the current time, the elapsed time and the totals automatically (every 30 seconds).

---

## Alternative Flows

### AF-1: No Time Entries Today

**Branches from:** Main Flow step 1
**Condition:** No TimeEntries exist for today

1. System displays an empty timeline.
2. System displays "Total hours today: 0h 0m".
3. System shows both buttons, "Check-In" emphasized.
4. System displays a message: "No time entries recorded yet. Click 'Check In' to start."
5. Use case continues normally.

### AF-2: Open Entry

**Branches from:** Main Flow step 3
**Condition:** An open TimeEntry exists

1. System calculates total hours from completed entries.
2. System adds the elapsed time of the open entry (current server time - check-in time).
3. System displays the total (e.g., "Total hours today: 2h 45m") and beneath it how it is made up: "2h 0m completed + 0h 45m on the open entry".
4. Use case continues.

### AF-3: Database Error Loading Entries

**Branches from:** Main Flow step 1
**Condition:** Database query fails

1. System displays an error message: "Unable to load time entries. Please try again."
2. "Retry" button is displayed.
3. No timeline, status or totals are shown.
4. Use case ends; user can retry (which repeats Main Flow step 1) or navigate away.

---

## Postconditions

- **On success:**
  - Timeline is displayed with all entries for today
  - Total worked hours are calculated and displayed
  - Buttons and the edit control are shown

- **On failure:**
  - Error message is displayed
  - No timeline or totals are shown
  - User can retry or navigate away

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only entries for today (current date in the user's time zone) are displayed, plus an open entry that started earlier (for example across midnight), so the employee can always see and end it |
| BR-02 | Entries are displayed in chronological order (earliest check-in first) |
| BR-03 | Total hours include all completed entries plus elapsed time for the open entry |
| BR-04 | Public holidays do not affect display or calculation (informational only) |

---

## Tests

- [x] Main Flow covered (steps 1–8)
- [x] AF-1 (No Entries) covered
- [x] AF-2 (Open Entry) covered
- [x] AF-3 (Database Error) covered
- [x] BR-01–BR-04 covered

---

## UI Surface

- **Time Tracking Dashboard / Today View:** Displays daily timeline and summary.
  - Timeline list showing all time entries with check-in, check-out, duration, and status.
  - Total worked hours summary (e.g., "Total hours today: 7h 45m") and break time.
  - Current time display and, while working, the elapsed time.
  - Check-In / Check-Out buttons.
  - Edit control for each entry (disabled until UC-004).
  - Empty-state hint, and an error message with a Retry button when loading fails.

| Page | Access |
|------|--------|
| Today's Time Tracking (home page) | Authenticated Employee |
