# UC-017: Regional Public Holidays

---

**Goal:** As an administrator, I want to keep public holidays per region and assign every employee a region, so that people working in different parts of the world see the holidays of their own region, and a manager reviewing a timesheet sees the holidays of the employee's region.

**Status:** Implemented
**Date:** 2026-09-26

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

> **Note:** This use case replaces the single company-wide list of public holidays (UC-012, assumption A6) by one list per region. Regions are kept by the administrator in the application (no fixed list of countries), so a region can be a country, a state or any other area, for example "Germany – Bavaria", "USA" or "India".

---

## Actors

- **Primary actor:** Administrator (authenticated with ADMIN role)
- **Secondary actors:** Employees, managers and administrators as the people who look at timesheets and see the holidays of the region; no external system.

---

## Preconditions

- The administrator is signed in and active.
- At least one region exists. The migration creates the region "Default" and assigns every existing employee and holiday to it, so this is always the case.

---

## Trigger

The administrator opens "Public holidays" (or the employee form) and adds, renames or deletes a region, adds a holiday for a region, or sets the region of an employee.

---

## Main Flow

1. Administrator opens "Public holidays".
2. System shows a region selector (the first region, ordered by name, is chosen), the year filter and the holidays of the chosen region and year.
3. Administrator opens "Manage regions" and enters the name of a new region, for example "Germany – Bavaria", and saves.
4. System stores the region with an audit entry and shows it in the region selector.
5. Administrator chooses the region in the selector and clicks "Add holiday".
6. System shows the form with the date and the name; the region is shown and cannot be changed.
7. Administrator enters date and name and saves.
8. System stores the holiday for that region with an audit entry, shows a success message and lists the holiday under that region.
9. Administrator opens "Manage employees" and adds or edits an employee, chooses the employee's region in the required field "Region" (new employees start with the first region) and saves.
10. System stores the employee with the region and writes the usual audit entry with the old and the new region.
11. An employee opens "My Timesheet". System marks only the days that are public holidays in the employee's region and shows the note "Public holidays: <region>" with the month.
12. A manager opens the timesheet of an employee to review it or to look at it (UC-007, UC-009, UC-010), or an administrator looks at it (UC-014). System marks the holidays of the employee's region, not those of the viewer, and names that region in the note.

---

## Alternative Flows

### AF-1: Invalid Region Name

**Branches from:** Main Flow step 3
**Condition:** The name is empty, longer than 100 characters, or another region already has the same name (ignoring case and surrounding spaces)

1. System shows an error next to the name ("Please enter a region name.", "The name is too long." or "A region with this name already exists.").
2. Administrator stays in the dialog and corrects the name.
3. Returns to Main Flow step 3.

### AF-2: Date Already a Holiday in This Region

**Branches from:** Main Flow step 7
**Condition:** The region already has a holiday on the chosen date

1. System shows the error "A holiday is already configured for [Date] in [Region]. Please select a different date or edit the existing holiday." Nothing is stored.
2. Administrator stays in the form and changes the date. The same date in another region is allowed.
3. Returns to Main Flow step 7.

### AF-3: Region Still in Use

**Branches from:** Main Flow, deleting a region in "Manage regions"
**Condition:** Employees are assigned to the region, or the region has holidays, or it is the last region

1. System refuses the deletion and says why: "This region cannot be deleted: [N] employees are assigned to it. Give them another region first.", "This region cannot be deleted: it still has [N] public holidays. Delete them first." or "The last region cannot be deleted." (The last region is checked first.)
2. Nothing is deleted.
3. Use case ends.

### AF-4: Somebody Else Changed the Region

**Branches from:** Main Flow step 3, renaming a region
**Condition:** The region was changed by another administrator after the dialog was opened

1. System refuses the save and stores nothing.
2. The list of regions is reloaded with the current data.
3. System shows: "This region was changed by someone else in the meantime. Nothing was saved. Please check the current data and try again."
4. Use case ends.

### AF-5: Employee Without Region

**Branches from:** Main Flow step 9
**Condition:** The employee data has no region or an unknown one (the form always has a region chosen and cannot empty it; this protects the service)

1. System refuses the save, stores nothing, and the form would show "Please choose a region." next to the field.
2. Returns to Main Flow step 9.

### AF-6: Database Error

**Branches from:** Main Flow steps 4, 8 and 10
**Condition:** The database operation fails

1. System shows "Unable to save changes. Please try again." and stores nothing (change and audit entry are one transaction).
2. Administrator stays in the dialog and can try again.
3. Use case ends without a change.

### AF-7: Administrator Cancels

**Branches from:** Main Flow steps 3, 7 and 9
**Condition:** Administrator cancels a dialog or form

1. The dialog closes and nothing is changed.
2. Use case ends.

### AF-8: Region of an Employee Changes

**Branches from:** Main Flow step 10
**Condition:** The administrator gives an employee another region

1. From now on all months of this employee, past and future, show the holidays of the new region. There is no history of regions.
2. Worked time, timesheets and their status are not changed.
3. Use case ends.

---

## Postconditions

- **On success:** Regions, their holidays and the employees' regions are stored with audit entries. Every timesheet view marks the holidays of the region of the employee whose timesheet it is and names the region.
- **On failure:** Nothing is changed, an error message is shown, and the administrator can try again or cancel.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only active administrators manage regions and holidays and set the region of an employee; employees cannot change their own region and the employee's own settings do not contain it |
| BR-02 | Every employee has exactly one region (required). The migration creates the region "Default" (it can be renamed) and assigns every existing employee and every existing holiday to it |
| BR-03 | A holiday belongs to exactly one region. Its date is unique within the region, not for the whole company (this replaces UC-012 BR-02); the database enforces it too |
| BR-04 | Region names are trimmed, 1 to 100 characters, and unique ignoring case |
| BR-05 | A region can only be deleted when no employee is assigned to it and it has no holidays; the last region can never be deleted |
| BR-06 | A timesheet view always shows the holidays of the region of the employee whose timesheet it is, never of the person looking at it. This holds for the employee, the manager, the reviewer and the administrator |
| BR-07 | Adding, renaming and deleting a region, and every holiday change, are audited with the old and new values, in the same transaction as the change; a rename is protected against concurrent changes (version) |
| BR-08 | Public holidays stay informational: they never change worked time or totals (UC-012 BR-06); their names are not translated |
| BR-09 | Editing a holiday cannot move it to another region; the region is shown but read-only (delete and add again to move it) |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [x] Main Flow covered (steps 1–12), including a manager in another region than the employee
- [x] AF-1 (Invalid region name) covered
- [x] AF-2 (Date already a holiday in the region; the same date in another region is accepted) covered
- [x] AF-3 (Region still in use, last region) covered
- [x] AF-4 (Region changed by someone else) covered
- [x] AF-5 (Employee without region) covered
- [x] AF-6 (Database error) covered
- [x] AF-7 (Cancel) covered
- [x] AF-8 (Region of an employee changes, past months follow) covered
- [x] BR-01–BR-09 covered, including the migration result (region "Default", unique date per region)
- [x] Layout of the region selector, the "Manage regions" dialog, the employee form and the region note checked at desktop, tablet and phone size

---

## UI Surface

- **Public holidays page:** A region selector above the list (with the year filter as before), a "Manage regions" button, and the holidays of the chosen region and year. "Add holiday" and the row actions Edit and Delete work on the chosen region.
- **Manage regions dialog:** The list of regions with their number of employees and holidays, a field to add a region, and Rename and Delete for each region; a region that is in use shows why it cannot be deleted.
- **Add/Edit holiday form:** Date and name as before, plus the region shown as read-only text.
- **Employee form:** A required "Region" selection, and the region is also shown on the employee's detail page.
- **Timesheet views:** A note "Public holidays: <region>" next to the month, on the employee's own timesheet and on the timesheet of an employee seen by a manager or administrator.

| Page | Access |
|------|--------|
| Public holidays with regions | Administrator |
| Employee add/edit form and detail page with region | Administrator |
| Timesheet views with the region note | Authenticated |
