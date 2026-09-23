# UC-011: Administrator Manage Employees

---

**Goal:** As an administrator, I want to create, edit, and deactivate employee records so that the employee database remains current and accurate.

**Status:** Pending
**Date:** 2024-01-15

---

## Actors

- **Primary actor:** Administrator (authenticated with ADMIN role)

---

## Preconditions

- User is authenticated and has the ADMIN role
- For editing/deactivating: Employee record exists in the database

---

## Trigger

Administrator navigates to the "Employee Management" view and clicks to create, edit, or deactivate an employee.

---

## Main Flow

### Create Employee

1. Administrator clicks "Add Employee" button.
2. System displays a create employee form with fields:
   - Email (required, unique)
   - First Name (required)
   - Last Name (required)
   - Role (required, dropdown: EMPLOYEE, MANAGER, ADMIN)
   - Manager (optional, dropdown of existing managers/employees)
   - Department (required, dropdown)
3. Administrator fills in the form.
4. Administrator clicks "Save".
5. System validates:
   - Email is unique and valid format
   - First Name and Last Name are not empty
   - Role is selected
   - Department is selected
6. System creates Employee record with:
   - All provided fields
   - isActive = true (default)
   - createdAt = current timestamp
7. System creates audit log entry: action = CREATE, captures admin, timestamp, and new employee data.
8. System displays success message: "Employee [Name] created."
9. System returns to employee list.

### Edit Employee

10. Administrator selects an employee from the list.
11. Administrator clicks "Edit" button.
12. System displays edit form prepopulated with current employee data.
13. Administrator updates fields (email may be read-only or require special permission; role, manager, department are editable).
14. Administrator clicks "Save".
15. System validates as in step 5 (with unique constraint exception for email if not changed).
16. System compares old and new values.
17. System updates Employee record with new values.
18. System creates audit log entry: action = UPDATE, captures old values, new values, admin, and timestamp.
19. System displays success message: "Employee [Name] updated."
20. System returns to employee list.

### Deactivate Employee

21. Administrator selects an employee from the list.
22. Administrator clicks "Deactivate" button.
23. System displays confirmation dialog: "Deactivate [Employee Name]? This employee will no longer be able to log in."
24. Administrator confirms.
25. System sets isActive = false for the employee.
26. System creates audit log entry: action = UPDATE (or custom DELETE), captures reason and timestamp.
27. System displays success message: "Employee [Name] deactivated."
28. System returns to employee list.

---

## Alternative Flows

### AF-1: Email Already Exists (Create)

**Branches from:** Main Flow step 5 (Create validation)
**Condition:** Email is already in use

1. System displays error: "Email already exists. Please use a different email."
2. User remains in create form.
3. User corrects email and resubmits.
4. Returns to Main Flow step 5.

### AF-2: Invalid Email Format

**Branches from:** Main Flow step 5
**Condition:** Email format is invalid (e.g., missing @)

1. System displays error: "Please enter a valid email address."
2. User remains in form.
3. User corrects email and resubmits.
4. Returns to Main Flow step 5.

### AF-3: Missing Required Fields (Create/Edit)

**Branches from:** Main Flow step 5
**Condition:** First Name, Last Name, Role, or Department is empty

1. System displays error: "Please fill in all required fields."
2. User remains in form.
3. User fills in missing fields and resubmits.
4. Returns to Main Flow step 5.

### AF-4: Database Error During Create/Edit/Deactivate

**Branches from:** Main Flow steps 6, 17, or 25
**Condition:** Database operation fails

1. System displays error: "Unable to save changes. Please try again."
2. User remains in form (for create/edit) or sees retry option (for deactivate).
3. User can retry.
4. Use case ends without state change.

### AF-5: Administrator Cancels Create/Edit

**Branches from:** Main Flow steps 4 or 14
**Condition:** Administrator clicks "Cancel" button

1. Form closes without saving.
2. No changes are made.
3. System returns to employee list.
4. Use case ends.

### AF-6: Administrator Cancels Deactivation

**Branches from:** Main Flow step 24
**Condition:** Administrator clicks "Cancel" in confirmation dialog

1. Dialog closes.
2. Employee remains active.
3. System returns to employee list.
4. Use case ends.

---

## Postconditions

- **On success (Create):** 
  - New Employee record is created with isActive = true
  - Audit log entry captures all new values
  - Employee appears in employee list
  - Employee can now log in if IAM email matches

- **On success (Edit):** 
  - Employee record is updated with new values
  - Audit log entry captures changes
  - Employee list reflects updates
  - If role changed, access level is updated on next login

- **On success (Deactivate):** 
  - Employee isActive = false
  - Audit log entry captures deactivation
  - Employee is grayed out or marked as inactive in list
  - Employee can no longer log in
  - Historical data is retained

- **On failure:** 
  - No changes are made
  - Error message is displayed
  - User can retry or cancel

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Email must be unique and in valid format |
| BR-02 | First Name, Last Name, Role, and Department are mandatory fields |
| BR-03 | Manager field is optional; if blank, employee has no direct manager |
| BR-04 | Only admins can create, edit, or deactivate employees |
| BR-05 | Deactivation sets isActive = false (soft delete); historical data is retained |
| BR-06 | All changes are logged to AuditLog with admin context and old/new values |
| BR-07 | Email is typically immutable (read-only) to preserve audit trail integrity |

---

## Tests

- [ ] Main Flow Create covered (steps 1–9)
- [ ] Main Flow Edit covered (steps 10–20)
- [ ] Main Flow Deactivate covered (steps 21–28)
- [ ] AF-1 (Email Exists) covered
- [ ] AF-2 (Invalid Email) covered
- [ ] AF-3 (Missing Fields) covered
- [ ] AF-4 (Database Error) covered
- [ ] AF-5 (Cancel Create/Edit) covered
- [ ] AF-6 (Cancel Deactivate) covered
- [ ] BR-01–BR-07 covered

---

## UI Surface

- **Employee List:** Table showing all employees with name, email, role, department, manager, and active status.
- **Add Employee Button:** Prominent button to create new employee.
- **Edit Button:** Per-employee action to edit details.
- **Deactivate Button:** Per-employee action to deactivate.
- **Create/Edit Form:** Modal or page with form fields for employee data.
- **Confirmation Dialog:** For deactivate action, confirming destructive operation.
- **Success/Error Messages:** Clear feedback on create/edit/deactivate results.

| Page | Access |
|------|--------|
| Employee Management | Authenticated Administrator |
