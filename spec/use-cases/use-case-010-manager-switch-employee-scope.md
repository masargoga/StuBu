# UC-010: Manager Switch Between Direct Reports and Department Employees

---

**Goal:** As a manager, I want to toggle between viewing only my direct reports and all employees in my department so that I can manage different scopes of responsibility.

**Status:** Implemented
**Date:** 2024-01-15

> A use case cannot be marked as **Implemented** unless all criteria in the use case implementation workflow are fulfilled.

> **Revision:** The scope switcher is a pair of radio buttons, "Direct reports only" and "All department employees", at the top of the Approvals (UC-007) and Employees (UC-009) pages. Each option shows how many employees it covers (the manager themselves is never counted), and the selected option is always visible. Changing it reloads the list in place, so nothing else on the page is lost (BR-05: an entered search stays). The choice is kept for the session (BR-04), so both pages agree; a new session starts with the direct reports (BR-01). Without direct reports the page says "You have no direct reports." while the department option stays usable (AF-1). Without a department on the account the department option is disabled with the message "Department not configured for your account. Contact your administrator.", and an earlier department choice falls back to the direct reports (AF-2); the database does not allow an employee without a department, so this only guards against an incomplete record. If the list of the other scope cannot be loaded, the message "Unable to load employees. Please try again." is shown and the previous scope and list stay (AF-3). For an administrator the department scope covers all employees. The scope filters what the lists offer; whether a manager may open or decide about a timesheet is still decided by `ReviewerAuthorization` alone (spec section 6: the department in any case), so a timesheet is reachable by address whichever scope is selected.

---

## Actors

- **Primary actor:** Manager (authenticated with MANAGER role)

---

## Preconditions

- User is authenticated and has the MANAGER role
- Manager has at least one direct report
- Manager's employee record has departmentId set

---

## Trigger

Manager navigates to the "Pending Approvals" or "Employees" view and sees a scope switcher control.

---

## Main Flow

1. System displays a scope switcher (e.g., dropdown or toggle) showing:
   - "Direct Reports Only" (default)
   - "All Department Employees"
2. Manager selects a scope option.
3. System filters the employee/timesheet list based on the selected scope:
   - If "Direct Reports Only": display only employees where managerId = current manager's ID
   - If "All Department Employees": display all employees where departmentId = current manager's department
4. System refreshes the display (e.g., pending approvals list) to show only relevant employees.
5. System remembers the selected scope for the manager's session (or persists as user preference if applicable).
6. Manager can now navigate, review timesheets, and approve for the selected scope.

---

## Alternative Flows

### AF-1: Manager Has No Direct Reports

**Branches from:** Preconditions
**Condition:** Manager has no employees where managerId = current manager's ID

1. System displays message: "You have no direct reports."
2. "Direct Reports Only" option may be disabled or shown as 0 items.
3. Manager can select "All Department Employees" to view broader scope.
4. Use case continues.

### AF-2: Manager Not in a Department

**Branches from:** Preconditions
**Condition:** Manager's employee record has no departmentId

1. System displays message: "Department not configured for your account. Contact administrator."
2. "All Department Employees" option is disabled.
3. Manager can only view direct reports.
4. Use case continues.

### AF-3: Database Error Loading Employees

**Branches from:** Main Flow step 3
**Condition:** Database query fails

1. System displays error: "Unable to load employees. Please try again."
2. Previous scope filter remains active.
3. Manager can retry or navigate away.
4. Use case ends.

---

## Postconditions

- **On success:** 
  - Scope switcher shows selected option
  - Employee/timesheet list reflects the selected scope
  - Manager can continue with narrower or broader employee visibility

- **On failure:** 
  - Error message is displayed
  - Previous scope remains active
  - Manager can retry

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Default scope is "Direct Reports Only" |
| BR-02 | "All Department Employees" includes all employees in manager's department, not just direct reports |
| BR-03 | Manager can only view/approve timesheets for employees in the selected scope |
| BR-04 | Scope selection is session-specific or persistent per user preference |
| BR-05 | Switching scope does not lose any pending work or state |

---

## Tests

- [x] Main Flow covered (steps 1–6)
- [x] AF-1 (No Direct Reports) covered
- [x] AF-2 (No Department) covered
- [x] AF-3 (Database Error) covered
- [x] BR-01–BR-05 covered

---

## UI Surface

- **Scope Switcher:** Dropdown menu or toggle button showing "Direct Reports Only" and "All Department Employees" options.
- **Active Selection:** Clear indication of which scope is currently selected (e.g., highlighted, checkmark).
- **Employee List:** Updates to show only employees in the selected scope.
- **Error Messages:** If applicable, user-friendly error for misconfiguration.

| Page | Access |
|------|--------|
| Pending Approvals | Authenticated Manager |
| Employees List | Authenticated Manager |
