# UC-012: Administrator Configure Public Holidays

---

**Goal:** As an administrator, I want to add and remove public holidays so that the calendar is accurate for the organization.

**Status:** Implemented
**Date:** 2024-01-15

> A use case cannot be marked as **Implemented** unless all criteria in the use case implementation workflow are fulfilled.

> **Revision:** Administrators get a "Public holidays" entry in the navigation (`/admin/holidays`). The list shows the holidays of one year, earliest first (the current year is chosen first; "All years" shows everything), with "Edit" and "Delete" for each. Adding and editing use one form with a date picker (following the language of the page) and the name; both date and name can be edited, and a date that belongs to another holiday is refused. Dates from the year 2000 to 2100 are accepted, past and future alike (BR-04); the date picker cannot report unreadable input, so an empty date is answered with "Please enter a valid date.". Deleting is permanent after a confirmation (the audit entry keeps the date and name). Editing without changes writes no audit entry. Every change is stored together with its audit entry (CREATE, UPDATE with old and new values, DELETE with the old values) in one transaction, and only active administrators may call the service. The unique date is also enforced by the database, so two administrators adding the same date at the same moment cannot both succeed. Holidays show up in the month views of employees and managers (UC-005, UC-009) and never change worked time (BR-06).

---

## Actors

- **Primary actor:** Administrator (authenticated with ADMIN role)

---

## Preconditions

- User is authenticated and has the ADMIN role

---

## Trigger

Administrator navigates to the "Public Holidays" configuration view and clicks to add, edit, or delete a holiday.

---

## Main Flow

### Add Holiday

1. Administrator clicks "Add Holiday" button.
2. System displays an add holiday form with fields:
   - Date (required, date picker)
   - Holiday Name (required, text field, e.g., "Christmas", "New Year's Day")
3. Administrator selects a date and enters a name.
4. Administrator clicks "Save".
5. System validates:
   - Date is valid and not in the past (or allow past dates, depending on policy)
   - Holiday Name is not empty
   - Date does not already have a holiday configured
6. System creates PublicHoliday record with:
   - date = selected date
   - name = entered name
   - createdAt = current timestamp
7. System creates audit log entry: action = CREATE, captures admin, timestamp, and holiday data.
8. System displays success message: "Holiday [Name] added on [Date]."
9. System returns to public holidays list.

### Edit Holiday

10. Administrator selects a holiday from the list.
11. Administrator clicks "Edit" button.
12. System displays edit form prepopulated with:
    - Date (may be read-only)
    - Holiday Name
13. Administrator updates the holiday name (and optionally date if editable).
14. Administrator clicks "Save".
15. System validates as in step 5.
16. System compares old and new values.
17. System updates PublicHoliday record.
18. System creates audit log entry: action = UPDATE, captures old values, new values, admin, and timestamp.
19. System displays success message: "Holiday updated."
20. System returns to public holidays list.

### Delete Holiday

21. Administrator selects a holiday from the list.
22. Administrator clicks "Delete" button.
23. System displays confirmation dialog: "Delete [Holiday Name] on [Date]? This cannot be undone."
24. Administrator confirms.
25. System deletes the PublicHoliday record (hard delete or soft delete with isDeleted flag).
26. System creates audit log entry: action = DELETE, captures deleted holiday data and timestamp.
27. System displays success message: "Holiday deleted."
28. System returns to public holidays list.

---

## Alternative Flows

### AF-1: Date Already Has a Holiday

**Branches from:** Main Flow step 5 (Add validation)
**Condition:** A PublicHoliday already exists for the selected date

1. System displays error: "A holiday is already configured for [Date]. Please select a different date or edit the existing holiday."
2. User remains in add form.
3. User selects a different date and resubmits.
4. Returns to Main Flow step 5.

### AF-2: Invalid Date (e.g., Malformed)

**Branches from:** Main Flow step 5
**Condition:** Date is invalid or not properly formatted

1. System displays error: "Please enter a valid date."
2. User remains in form.
3. User corrects date and resubmits.
4. Returns to Main Flow step 5.

### AF-3: Empty Holiday Name

**Branches from:** Main Flow step 5
**Condition:** Holiday Name field is empty

1. System displays error: "Please enter a holiday name."
2. User remains in form.
3. User enters name and resubmits.
4. Returns to Main Flow step 5.

### AF-4: Database Error

**Branches from:** Main Flow steps 6, 17, or 25
**Condition:** Database operation fails

1. System displays error: "Unable to save changes. Please try again."
2. User remains in form (for add/edit) or sees retry option (for delete).
3. User can retry.
4. Use case ends without state change.

### AF-5: Administrator Cancels

**Branches from:** Main Flow steps 4, 14, or 24
**Condition:** Administrator clicks "Cancel" button or in confirmation dialog

1. Dialog/form closes without saving.
2. No changes are made.
3. System returns to public holidays list.
4. Use case ends.

---

## Postconditions

- **On success (Add):** 
  - New PublicHoliday record is created
  - Audit log entry captures creation
  - Holiday appears in public holidays list
  - Calendar views may highlight this date

- **On success (Edit):** 
  - PublicHoliday record is updated
  - Audit log entry captures changes
  - Public holidays list reflects updates

- **On success (Delete):** 
  - PublicHoliday record is removed
  - Audit log entry captures deletion
  - Holiday no longer appears in list or calendar

- **On failure:** 
  - No changes are made
  - Error message is displayed
  - User can retry or cancel

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only admins can manage public holidays |
| BR-02 | Each date can have at most one public holiday entry |
| BR-03 | Holiday Name is required and should be human-readable |
| BR-04 | Dates can be in the past, present, or future (all allowed for archival and planning) |
| BR-05 | All changes are logged to AuditLog with admin context and old/new values |
| BR-06 | Public holidays are informational; they do not automatically exclude days from timesheet calculations |

---

## Tests

- [x] Main Flow Add covered (steps 1–9)
- [x] Main Flow Edit covered (steps 10–20)
- [x] Main Flow Delete covered (steps 21–28)
- [x] AF-1 (Date Exists) covered
- [x] AF-2 (Invalid Date) covered
- [x] AF-3 (Empty Name) covered
- [x] AF-4 (Database Error) covered
- [x] AF-5 (Cancel) covered
- [x] BR-01–BR-06 covered

---

## UI Surface

- **Public Holidays List:** Table showing all configured holidays with date and name.
- **Add Holiday Button:** Prominent button to create new holiday.
- **Edit Button:** Per-holiday action to edit details.
- **Delete Button:** Per-holiday action to delete.
- **Add/Edit Form:** Modal or page with date picker and name field.
- **Confirmation Dialog:** For delete action, confirming destructive operation.
- **Success/Error Messages:** Clear feedback on add/edit/delete results.

| Page | Access |
|------|--------|
| Public Holidays Management | Authenticated Administrator |
