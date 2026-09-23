# UC-002: Record Check-In and Check-Out

---

**Goal:** As an employee, I want to record my work times (check-in and check-out) so that my working hours are accurately tracked.

**Status:** Pending
**Date:** 2024-01-15

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
2. System creates a new TimeEntry for today with:
   - `date` = today's date
   - `checkInTime` = current time
   - `checkOutTime` = null
   - `isActive` = true
3. System stores the TimeEntry in the database.
4. System displays the active time entry in the timeline.
5. System displays a message: "Checked in at [time]."
6. System marks the "Check In" button as inactive (greyed out) and enables "Check Out" button.

### Check-Out Flow

7. Employee clicks "Check Out" button.
8. System updates the active TimeEntry:
   - `checkOutTime` = current time
   - `isActive` = false
9. System stores the updated TimeEntry in the database.
10. System calculates the duration: `checkOutTime - checkInTime`.
11. System adds the duration to the day's total worked hours.
12. System displays a message: "Checked out at [time]. Worked [duration]."
13. System marks the "Check Out" button as inactive and re-enables "Check In" button.
14. System shows the completed time entry in the timeline.

---

## Alternative Flows

### AF-1: Check-In When Already Active

**Branches from:** Main Flow step 1 (Check-In)
**Condition:** An active TimeEntry already exists for today

1. System displays an error message: "You are already checked in. Please check out first."
2. User may click "Check Out" or close the message.
3. Returns to Main Flow step 1 (user clicks "Check In" again after checking out).

### AF-2: Check-Out Without Active Check-In

**Branches from:** Main Flow step 7 (Check-Out)
**Condition:** No active TimeEntry exists for today

1. System displays an error message: "You are not currently checked in."
2. User is prompted to click "Check In" first.
3. Returns to Main Flow step 1.

### AF-3: Database Error During Check-In

**Branches from:** Main Flow step 3
**Condition:** Database fails to create TimeEntry

1. System displays an error message: "Check-in failed. Please try again."
2. "Check In" button remains enabled.
3. User can retry.
4. Use case ends without state change.

### AF-4: Database Error During Check-Out

**Branches from:** Main Flow step 9
**Condition:** Database fails to update TimeEntry

1. System displays an error message: "Check-out failed. Please try again."
2. System retains active status for the TimeEntry.
3. "Check Out" button remains enabled.
4. User can retry.
5. Use case ends without state change.

---

## Postconditions

- **On success (Check-In):** 
  - A new active TimeEntry is created in the database
  - Timeline is updated to show the new entry
  - Audit log entry is created with action CREATE

- **On success (Check-Out):** 
  - The active TimeEntry is updated with check-out time
  - Timeline is updated to show the completed entry
  - Day's total worked hours are recalculated
  - Audit log entry is created with action UPDATE

- **On failure:** 
  - No TimeEntry is created or modified
  - User sees an error message
  - UI state remains unchanged, allowing retry

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only one TimeEntry per employee can be active at a time |
| BR-02 | Check-in and check-out times use the current system time (no manual time entry in this flow) |
| BR-03 | Multiple time entries are allowed on the same day |
| BR-04 | All time entries must be created/updated by the employee who owns them |
| BR-05 | Time entries cannot be created for future dates |

---

## Tests

- [ ] Main Flow Check-In covered (steps 1–6)
- [ ] Main Flow Check-Out covered (steps 7–14)
- [ ] AF-1 (Already Active) covered
- [ ] AF-2 (Not Checked In) covered
- [ ] AF-3 (Check-In Database Error) covered
- [ ] AF-4 (Check-Out Database Error) covered
- [ ] BR-01–BR-05 covered

---

## UI Surface

- **Time Tracking Panel:** Shows current day's timeline with all time entries and total worked hours.
- **Check-In / Check-Out Buttons:** Prominent buttons to start/end work periods. Only one is enabled at a time.
- **Timeline Display:** Shows all completed time entries for the day with check-in and check-out times, duration, and status.
- **Messages:** Success and error messages displayed prominently.

| Page | Access |
|------|--------|
| Time Tracking Dashboard | Authenticated Employee |
