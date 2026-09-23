# UC-014: Administrator View All Employee and Timesheet Data

---

**Goal:** As an administrator, I want to view all employee records and their timesheets so that I have complete organizational visibility.

**Status:** Pending
**Date:** 2024-01-15

---

## Actors

- **Primary actor:** Administrator (authenticated with ADMIN role)

---

## Preconditions

- User is authenticated and has the ADMIN role
- At least one employee exists in the database

---

## Trigger

Administrator navigates to the "Employees & Timesheets" or "Admin Dashboard" view.

---

## Main Flow

### View All Employees

1. System displays a table/list of all employees (active and inactive) with columns:
   - Name (First + Last)
   - Email
   - Role (EMPLOYEE, MANAGER, ADMIN)
   - Manager (name or "None")
   - Department
   - Active Status (Active / Inactive)
   - Last Login (timestamp, optional)
2. Administrator can sort by any column.
3. Administrator can search for an employee by name, email, or department.
4. Administrator can filter by:
   - Status (Active / Inactive)
   - Role (EMPLOYEE, MANAGER, ADMIN)
   - Department (dropdown)
5. Administrator applies search/filters.
6. System displays filtered employee list.

### View Employee Details and Timesheets

7. Administrator clicks on an employee to view details.
8. System displays:
   - Full employee information (name, email, role, manager, department, created date, etc.)
   - List of all timesheets for the employee with status (DRAFT, SUBMITTED, APPROVED, REJECTED)
9. Administrator selects a timesheet to view.
10. System displays the complete timesheet with:
    - Month and year
    - All time entries
    - Daily and monthly totals
    - Status and approval/rejection history
11. Administrator can drill down into individual days or entries for detailed timeline view.
12. Administrator can view approval/rejection comments (from manager or previous submission history).

---

## Alternative Flows

### AF-1: No Employees

**Branches from:** Main Flow step 1
**Condition:** No employees exist in the database

1. System displays message: "No employees found."
2. Administrator can create an employee using UC-011.
3. Use case ends.

### AF-2: No Search Results

**Branches from:** Main Flow step 6
**Condition:** Search or filter returns no matches

1. System displays message: "No employees match your filters."
2. Administrator can adjust search/filters and try again.
3. Use case ends or loops to step 5.

### AF-3: No Timesheets for Employee

**Branches from:** Main Flow step 8
**Condition:** Employee has no timesheet records

1. System displays message: "No timesheets found for this employee."
2. Administrator can navigate back to employee list.
3. Use case ends.

### AF-4: Database Error Loading Data

**Branches from:** Main Flow steps 1 or 6
**Condition:** Database query fails

1. System displays error: "Unable to load data. Please try again."
2. "Retry" button is shown.
3. Use case ends; administrator can retry or navigate away.

---

## Postconditions

- **On success:** 
  - Administrator can see all employees and their complete timesheet history
  - Administrator has complete organizational visibility
  - Administrator can verify data integrity and make decisions

- **On failure:** 
  - Error message is displayed
  - Administrator can retry or navigate away

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only admins have access to view all employee and timesheet data |
| BR-02 | Both active and inactive employees are visible (but may be filtered) |
| BR-03 | All timesheets (all statuses) are visible for an employee |
| BR-04 | Administrator has read-only access through this use case (edits use UC-011 or related approval flows) |
| BR-05 | Search and filter are case-insensitive for text fields |

---

## Tests

- [ ] Main Flow View Employees covered (steps 1–6)
- [ ] Main Flow View Details covered (steps 7–12)
- [ ] AF-1 (No Employees) covered
- [ ] AF-2 (No Search Results) covered
- [ ] AF-3 (No Timesheets) covered
- [ ] AF-4 (Database Error) covered
- [ ] BR-01–BR-05 covered

---

## UI Surface

- **Employee List:** Table showing all employees with search and filter controls.
- **Sort:** Column headers are clickable to sort.
- **Search Bar:** Text input to search by name or email.
- **Filter Controls:** Dropdowns for status, role, and department.
- **Employee Detail Panel:** Shows selected employee's information and list of timesheets.
- **Timesheet Detail Panel:** Shows complete timesheet with entries and approval history.
- **Messages:** For empty results or errors.

| Page | Access |
|------|--------|
| Employee & Timesheet Directory | Authenticated Administrator |
