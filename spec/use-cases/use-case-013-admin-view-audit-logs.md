# UC-013: Administrator View Audit Logs

---

**Goal:** As an administrator, I want to view and search audit logs so that I can monitor system changes and ensure compliance.

**Status:** Pending
**Date:** 2024-01-15

---

## Actors

- **Primary actor:** Administrator (authenticated with ADMIN role)

---

## Preconditions

- User is authenticated and has the ADMIN role
- At least one audit log entry exists in the database

---

## Trigger

Administrator navigates to the "Audit Logs" view.

---

## Main Flow

1. System displays the audit logs list with default sorting (most recent first).
2. System shows a table with columns:
   - Timestamp (when the action occurred)
   - User (who performed the action, or "System" if automated)
   - Entity Type (e.g., "Employee", "TimeEntry", "Timesheet")
   - Entity ID (ID of the affected entity)
   - Action (e.g., CREATE, UPDATE, DELETE, APPROVE, REJECT, SUBMIT)
   - Reason (optional; e.g., rejection reason)
   - Changes (summary; click to expand for detailed old/new values)
3. Administrator can scroll through the log list.
4. Administrator can click on an audit log entry to view detailed information:
   - Full timestamp
   - User ID and email
   - Entity type and ID
   - Action performed
   - Old values (JSON or formatted)
   - New values (JSON or formatted)
   - Reason (if applicable)
5. Administrator can use filters/search to narrow results:
   - Date range filter (From Date, To Date)
   - User filter (by email or name)
   - Entity Type filter (dropdown: Employee, TimeEntry, Timesheet, etc.)
   - Action filter (dropdown: CREATE, UPDATE, DELETE, etc.)
6. Administrator enters filter criteria and clicks "Search" or "Apply Filters".
7. System queries AuditLog with the filter criteria.
8. System displays filtered results.
9. Administrator reviews the audit trail for compliance or troubleshooting.

---

## Alternative Flows

### AF-1: No Audit Log Entries

**Branches from:** Main Flow step 1
**Condition:** Database contains no audit log entries

1. System displays message: "No audit logs found."
2. Administrator may apply filters or check back later.
3. Use case ends.

### AF-2: No Results for Filters

**Branches from:** Main Flow step 8
**Condition:** Filter criteria return no matches

1. System displays message: "No audit logs match your filters."
2. Administrator can adjust filters and search again.
3. Use case ends or loops to step 6.

### AF-3: Database Error Loading Logs

**Branches from:** Main Flow step 1 or 7
**Condition:** Database query fails

1. System displays error: "Unable to load audit logs. Please try again."
2. "Retry" button is shown.
3. Use case ends; administrator can retry or navigate away.

### AF-4: Export Audit Logs (Optional Feature)

**Branches from:** Main Flow step 9
**Condition:** Administrator clicks "Export" button (if available)

1. System exports filtered audit logs to CSV or PDF format.
2. System initiates file download.
3. Administrator receives the exported file.
4. Use case ends.

---

## Postconditions

- **On success:** 
  - Administrator can view audit logs with filtering and search capabilities
  - Detailed information is available for each entry
  - Administrator can make compliance and security decisions based on the logs

- **On failure:** 
  - Error message is displayed
  - Administrator can retry or navigate away

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Only admins can view audit logs |
| BR-02 | All significant data changes must be logged (CREATE, UPDATE, DELETE, APPROVE, REJECT, SUBMIT) |
| BR-03 | Audit logs are immutable; once created, they cannot be edited or deleted |
| BR-04 | Logs include timestamp, user context, entity info, action, and old/new values |
| BR-05 | Logs may include optional reason field (e.g., for rejection or correction) |
| BR-06 | System events (if any) may have null user_id (logged as "System") |

---

## Tests

- [ ] Main Flow covered (steps 1–9)
- [ ] AF-1 (No Entries) covered
- [ ] AF-2 (No Results) covered
- [ ] AF-3 (Database Error) covered
- [ ] AF-4 (Export) covered if applicable
- [ ] BR-01–BR-06 covered

---

## UI Surface

- **Audit Logs List:** Table showing all logs with timestamp, user, entity type, action, and changes.
- **Detail View:** Clickable row to expand and show full old/new values.
- **Filter Controls:** Date range picker, user search, entity type dropdown, action dropdown.
- **Search Button:** To apply filters and update results.
- **Export Button (Optional):** To download filtered logs as CSV or PDF.
- **Messages:** For empty results or errors.

| Page | Access |
|------|--------|
| Audit Logs | Authenticated Administrator |
