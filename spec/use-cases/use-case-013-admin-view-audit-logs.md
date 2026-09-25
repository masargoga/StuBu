# UC-013: Administrator View Audit Logs

---

**Goal:** As an administrator, I want to view and search audit logs so that I can monitor system changes and ensure compliance.

**Status:** Implemented
**Date:** 2024-01-15

> A use case cannot be marked as **Implemented** unless all criteria in the use case implementation workflow are fulfilled.

> **Revision:** Administrators get an "Audit log" entry in the navigation (`/admin/audit`). The list shows 25 entries per page, newest first, with time (in the browser's time zone), user, entity type and id, action, reason, a one-line summary of the changes and a "Details" button that opens the full entry: exact time, user name, email and id, entity, action, reason, and the old and new values with one line per field. The summary and the detail lines are made from the stored JSON values (an update shows `field: old → new`). Filters: from and to date (both days included, in the browser's time zone), user (a part of the name or email address; "System" finds automatic entries without a user), entity type (the types that occur in the log) and action; "Search" applies them, "Reset filters" clears them, and the current filter also applies to the export. Without any entry the page says "No audit logs found.", with a filter that matches nothing "No audit logs match your filters.". The optional export (AF-4) is implemented as a CSV download of the matching entries (at most 10,000 rows, UTF-8 with a byte order mark, cells that a spreadsheet could take for a formula are marked as text). The log is read-only by construction (BR-03): the repository has no update or delete operation (a test guards that, which is why it does not use Spring Data's specification executor), the entity has no setters, and the page has no editing controls. Reading the log is itself not audited.

> **Revision (after the first release):** Audit entries are also protected in the database: on PostgreSQL triggers refuse `UPDATE`, `DELETE` and `TRUNCATE` on the audit table (migration `V7`), in addition to the application having no such operation (BR-03). Indexes on the user and the action support the user filter and the last-login lookup of UC-014.

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

- [x] Main Flow covered (steps 1–9)
- [x] AF-1 (No Entries) covered
- [x] AF-2 (No Results) covered
- [x] AF-3 (Database Error) covered
- [x] AF-4 (Export) covered if applicable
- [x] BR-01–BR-06 covered

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
