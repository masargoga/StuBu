# UC-002: Record Check-In and Check-Out

---

**Goal:** As an employee, I want to record my work times (check-in and check-out) so that my working hours are accurately tracked.

**Status:** Implemented
**Date:** 2024-01-15

> A use case cannot be marked as **Implemented** unless all criteria in the use case implementation workflow are fulfilled.

> **Revision:** AF-1 and AF-2 follow `spec.md` sections 9 and 10 (confirmation dialogs instead of plain errors). For that reason both buttons stay available at all times; the relevant one is emphasized instead of the other being disabled.

> **Revision (design refresh):** The panel is a status card and a day card. The status card shows whether the employee is working (green dot, "Currently working since ..."), the date, the current time, the elapsed time and the two actions (the possible one is filled green, the other outlined). Below it three tiles show "Total hours today", "Break" and "First check-in" (the last only when there are entries), then the card "Your day" with the timeline. Each tile is one readable text ("Total hours today: 5h 34m").

> **Revision (time fields):** The time field of the "forgot to check in" dialog opens a list of times in 15-minute steps when the field or its clock icon is clicked; any minute can still be typed.

---

## Actors

- **Primary actor:** Employee (authenticated)

---

## Preconditions

- User is authenticated and has the EMPLOYEE role
- User is viewing the current day's time tracking page
- Current date and time are within business hours (no specific enforcement in system; employees can log any time)

---

## Trigger

Employee clicks "Check In" button to start a new work period or "Check Out" button to end the current work period.

---

## Main Flow

### Check-In Flow

1. Employee clicks "Check In" button.
2. System creates a new TimeEntry (open work period) for the employee with:
   - `checkInAt` = current server time
   - `checkOutAt` = null (open)
3. System stores the TimeEntry in the database.
4. System displays the open time entry in the timeline.
5. System displays a message: "Checked in at [time]."
6. System shows the status "Currently working since [time]" and emphasizes the "Check Out" button.

### Check-Out Flow

7. Employee clicks "Check Out" button.
8. System updates the open TimeEntry:
   - `checkOutAt` = current server time
9. System stores the updated TimeEntry in the database.
10. System calculates the duration: `checkOutAt - checkInAt`.
11. System adds the duration to the day's total worked time and recalculates the day's break time (gaps between work periods).
12. System displays a message: "Checked out at [time]. Worked [duration]."
13. System shows the status "Not checked in" and emphasizes the "Check In" button.
14. System shows the completed time entry in the timeline.

---

## Alternative Flows

### AF-1: Check-In When Already Active

**Branches from:** Main Flow step 1 (Check-In)
**Condition:** An open TimeEntry already exists for the employee

1. System does not create another open TimeEntry and does not change the existing one.
2. System asks: "You are already checked in since [time]. Do you want to replace this check-in time?" with the choices "Cancel" and "Replace Check-In".
3. If the employee chooses "Cancel", the dialog closes and nothing changes. Use case ends.
4. If the employee chooses "Replace Check-In":
   1. System sets the open TimeEntry's `checkInAt` to the current server time and stores it.
   2. System displays a message: "Check-in replaced. You are checked in since [time]."
   3. System writes an audit entry (UPDATE) with the old and new check-in time.
   4. Use case ends.

### AF-2: Check-Out Without an Open Check-In

**Branches from:** Main Flow step 7 (Check-Out)
**Condition:** The employee has no open TimeEntry

1. System does not reject the operation. It asks: "No open check-in was found. When did you start working?" with a date and a time input and the choices "Cancel" and "Confirm".
2. If the employee chooses "Cancel", the dialog closes and nothing changes. Use case ends.
3. If the employee chooses "Confirm", the system validates the input:
   - Date and time must both be entered, otherwise: "Please enter the date and time you started working."
   - The start must be in the past, otherwise: "The start time must be in the past."
   - The new work period must not overlap a work period the employee already recorded, otherwise: "This period overlaps a work period you already recorded."
   - If the input is invalid, the message is shown in the dialog, which stays open for correction.
4. If the input is valid:
   1. System creates a completed TimeEntry with `checkInAt` = the entered start and `checkOutAt` = current server time.
   2. System displays a message: "Checked out at [time]. Worked [duration]."
   3. System writes an audit entry (CREATE).
   4. Use case ends.
5. If, while the dialog was open, the employee became checked in (for example in another browser tab), the system creates nothing and displays: "You are already checked in since [time]."

### AF-3: Database Error During Check-In

**Branches from:** Main Flow step 3
**Condition:** Database fails to create TimeEntry

1. System displays an error message: "Check-in failed. Please try again."
2. "Check In" button remains available.
3. User can retry.
4. Use case ends without state change.

### AF-4: Database Error During Check-Out

**Branches from:** Main Flow step 9
**Condition:** Database fails to update TimeEntry

1. System displays an error message: "Check-out failed. Please try again."
2. System retains the open status of the TimeEntry.
3. "Check Out" button remains available.
4. User can retry.
5. Use case ends without state change.

---

## Postconditions

- **On success (Check-In):**
  - A new open TimeEntry is created in the database
  - Timeline is updated to show the new entry
  - Audit log entry is created with action CREATE

- **On success (Check-Out):**
  - The open TimeEntry is updated with the check-out time
  - Timeline is updated to show the completed entry
  - Day's total worked time and break time are recalculated
  - Audit log entry is created with action UPDATE

- **On success (AF-1 replace):** the open TimeEntry has the new check-in time and an UPDATE audit entry records old and new value.

- **On success (AF-2 confirm):** a completed TimeEntry exists for the entered period and a CREATE audit entry is written.

- **On failure:**
  - No TimeEntry is created or modified
  - User sees an error message
  - UI state remains unchanged, allowing retry

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only one open TimeEntry per employee can exist at a time, also with concurrent sessions or servers |
| BR-02 | Check-in and check-out times use the current server time, never the browser clock; the only time an employee enters is the start of a missing work period (AF-2) |
| BR-03 | Multiple time entries are allowed on the same day; break time is the sum of the gaps between them |
| BR-04 | Time entries can only be created or changed by the employee who owns them |
| BR-05 | Time entries cannot be created in the future: an entered start must lie before the current server time |
| BR-06 | A work period may cross midnight, and an entered work period must not overlap another of the employee's work periods |
| BR-07 | The day's worked time counts the full duration of every period that started today, plus the time elapsed on an open period |

---

## Tests

- [x] Main Flow Check-In covered (steps 1–6)
- [x] Main Flow Check-Out covered (steps 7–14)
- [x] AF-1 (Already Checked In: cancel, replace) covered
- [x] AF-2 (No Open Check-In: cancel, confirm, validation, concurrent check-in) covered
- [x] AF-3 (Check-In Database Error) covered
- [x] AF-4 (Check-Out Database Error) covered
- [x] BR-01–BR-07 covered

---

## UI Surface

- **Time Tracking Panel:** Shows the current working status, today's date, today's timeline with all time entries, and total worked and break time. It is part of the home page after login.
- **Check-In / Check-Out Buttons:** Large, prominent buttons to start/end work periods. Both stay available; the one that fits the current status is emphasized.
- **Timeline Display:** Shows the day's time entries as bars on a time scale that covers 06:00 to 20:00 and grows in whole hours when a period starts earlier or ends later (design refresh: the full 00-24 scale made the bars too small; every period is always fully visible), with check-in and check-out times, duration, and status (open periods look different from completed ones).
- **Messages:** Success and error messages displayed prominently and announced to screen readers.
- **Dialogs:** Replace check-in confirmation (AF-1); missing check-in time entry with date and time (AF-2).

| Page | Access |
|------|--------|
| Time Tracking Dashboard (home page) | Authenticated Employee |
