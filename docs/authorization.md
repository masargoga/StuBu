# Authorization model

Authentication is delegated to an OpenID Connect provider (Microsoft Entra ID, Google or another OIDC provider).
Authorization is decided by the application, from its own employee records, and enforced on the server. Nothing the
browser sends (an id in a URL, a hidden field) is trusted: every operation acts for the signed-in employee and checks
the rules again.

## Identity and roles

1. The user signs in at the provider. The application takes **only the email address** from the ID token.
2. `AuthenticationService` looks for an **active employee with that email**. No employee, or an inactive one: the login
   is refused with an explanation. An employee is never created from a login.
3. The role of the employee becomes the user's authorities: `EMPLOYEE` gives `EMPLOYEE`; `MANAGER` and `ADMIN` give
   their own authority **plus** `EMPLOYEE` (they record their own time too).
4. The session follows the employee record. `CurrentEmployeeFilter` compares the signed-in employee with the record at
   most every `stubu.security.recheck-interval` (5 seconds): when the employee was deactivated, deleted or given
   another role, the session is ended and the user is sent to the login page. A change of role therefore never applies
   half-way; the user signs in again and gets the new role.

## Who may open which page

| Route | Page | Employee | Manager | Administrator |
|-------|------|:--------:|:-------:|:-------------:|
| `/login` | Sign in | anonymous | anonymous | anonymous |
| `/` | Today: check in and out, timeline | yes | yes | yes |
| `/timesheet` | My monthly timesheet, submit | yes | yes | yes |
| `/approvals` | Timesheets waiting for approval | – | yes | – |
| `/approvals/review/{id}` | Review one timesheet, approve, reject | – | yes (see below) | look only |
| `/employees` | Employees of my scope | – | yes | yes |
| `/employees/timesheet/{id}` | An employee's month, read-only | – | yes (see below) | yes |
| `/admin/employees` | Manage all employees | – | – | yes |
| `/admin/employees/details/{id}` | One employee with all timesheets | – | – | yes |
| `/admin/holidays` | Public holidays | – | – | yes |
| `/admin/audit` | Audit log | – | – | yes |
| `/actuator/health/**` | Health probes of Docker and Kubernetes | anonymous | anonymous | anonymous |

Everything else is denied by default (`VaadinSecurityConfigurer`); a new HTTP endpoint has to declare its own rule.
The route restrictions are `@RolesAllowed` on the views and only hide pages: **the rules below are enforced again in
the services**, so a crafted request, a bookmarked URL or a second browser tab cannot get around them.

## Rules in the services

| Operation | Rule | Where |
|-----------|------|-------|
| Record, correct or delete time entries | Only for the signed-in employee, never for a given id; corrections only while the month's timesheet is `DRAFT` or `REJECTED` | `TimeEntryService`, `TimesheetStatus.allowsCorrections` |
| Submit a timesheet | Only the owner; the month has ended in the owner's time zone; at least one work period; none still open; the status is `DRAFT` or `REJECTED` | `TimesheetSubmissionService`, `SubmissionRules` |
| Look at another employee's timesheet | Manager: a direct report or somebody in the manager's own department. Administrator: everybody. Nobody looks at themselves through this route, and nobody looks at inactive employees | `ReviewerAuthorization.mayView` |
| Approve or reject | Manager only, and only for somebody the manager may view. **An administrator never decides** (spec.md section 22); nobody decides on their own timesheet; only a `SUBMITTED` timesheet can be decided | `ReviewerAuthorization.mayDecide`, `TimesheetReviewService` |
| Manage employees, public holidays; read all employee data; read and export the audit log | Active administrator only | `AdminAccess.require` at the start of every operation |
| Deactivate an employee | Not oneself; not the last active administrator | `EmployeeAdminService` |
| Change an employee's manager | No cycles (nobody can be their own manager, directly or indirectly) | `EmployeeAdminService` |

A refused operation (`AdminOnlyException`, `ReviewNotAllowedException`) is shown to the user as a plain "no permission"
message, never as a stack trace.

## Protecting the data itself

* Every change of employees, public holidays, time entries and timesheets, and every login, is written to the
  **audit log** in the same transaction as the change (who, when, what, old and new values, reason). The audit
  repository offers no update or delete operation; production should also withhold `UPDATE` and `DELETE` on the table
  from the application's database user.
* **Optimistic locking** (`version` columns) stops two people or two tabs from overwriting each other's changes:
  timesheets, time entries, employees and public holidays. The second save is refused with a message.
* The session cookie is `HttpOnly`, `SameSite=Lax` and `Secure` (HTTPS only) in production.
* Cross-site request forgery protection is provided by Vaadin and Spring Security; all state changes go through the
  Vaadin session.
* Passwords do not exist in the application (no local accounts), and tokens, credentials and client secrets are never
  logged.
