# Assumptions and rules that need validation

The application is meant for use in Germany. Following spec.md section 31 it **does not invent or hard-code legal
interpretations**. This page separates what the software does (technical), the assumptions the implementation made
where the specification left room, and the questions that the responsible legal and business stakeholders must answer.
None of it is legal advice.

## What the software does and does not do

* It **records actual working time** as the employee enters it (check-in and check-out by the server clock, or a
  correction afterwards). It never blocks or rewrites a recording because a legal limit might be exceeded.
* It derives break time from the gaps between work periods. It does not check minimum breaks, maximum daily hours,
  rest periods, night or Sunday work, overtime or allowances.
* It keeps an audit trail of every change (who, when, what, old and new values, reason).
* It does not connect to payroll, HR systems or export time data other than the audit log CSV.

## Assumptions made during implementation

| # | Assumption | Where |
|---|------------|-------|
| A1 | A month can be submitted only after it has ended in the employee's time zone. (Decision of the product owner.) | UC-006 |
| A2 | Time zone: times are stored as UTC instants and shown in the browser's time zone; the server zone is the fallback. "Today" and the month a day belongs to follow that zone. Emails use the configured `stubu.notifications.zone`. | architecture.md section 6 |
| A3 | Administrators may look at all employee data but never approve or reject (spec.md §22). Managers decide for their direct reports and their department, never for themselves. | authorization.md |
| A4 | A rejected timesheet stays "rejected" with its reason until the employee corrects it and submits again; corrections are allowed while it is a draft or rejected. | UC-007, UC-008 |
| A5 | Only the direct manager of the employee is emailed about a submission; a submitting employee without a manager only produces a log entry. | UC-006 |
| A6 | Public holidays are kept per region. Administrators maintain the regions and their holidays; every employee has one region and sees its holidays, and a manager sees the holidays of the employee's region. The region of an employee is the current one for all months (no history). Holidays are informational and do not change worked or expected time. | UC-012, UC-017, BR-11 |
| A7 | An employee's identity is their email address at the identity provider, compared case-insensitively. A changed address at the provider means a changed employee record. | UC-001 |
| A8 | Employees are never deleted, only deactivated, so that time records and audit entries keep their meaning. There is no "reactivate" action yet. | UC-011 |
| A9 | An open work period (checked in, not out) at the end of a month blocks submitting that month; the employee must check out or correct it first. | UC-006 |
| A10 | The audit log is kept without an expiry (its retention is not part of the one-year requirement). Login failures store the attempted email address. | UC-013 |
| A11 | The interface is available in English, German, Spanish and French. Everybody chooses the language in a selector (also on the login page); until then it follows the language of the browser. The choice is stored per employee, and emails use the recipient's language (the configured language when none is stored). The Spanish and French texts are first drafts and need a review by native speakers. | UC-001, UC-016 |
| A12 | The timelines show 06:00 to 20:00 and widen in whole hours to fit every work period, instead of the full 00:00-24:00 scale that spec.md section 12 names for the timesheet page (the full scale made the bars too small to read; a change agreed with the product owner in the design refresh). Every period is always fully visible. | UC-002, UC-005, design-system.md |

## To be validated by legal, works council and business stakeholders

1. **Working-time law.** Which requirements apply to the company's recording (for example the Working Hours Act's daily
   limits, breaks and rest periods, and the obligation to record working time as developed by European and German
   courts)? If some must be **checked or warned about**, define them as separate business rules; they must be
   configurable and must not be hidden in application logic (spec.md §31). Today none is enforced.
2. **Works council co-determination.** Introducing a system that can monitor employees usually needs an agreement with
   the works council. Confirm this is settled before rollout.
3. **Retention.** Confirm the retention period for time records (currently configured as one year, `P1Y`), whether the
   audit log and backups follow the same period, and how the deletion or anonymisation should work and be audited.
   Nothing is deleted today (spec.md §32).
4. **Data protection (GDPR).** Confirm the legal basis and the records of processing; the data stored is name, email,
   role, department, manager, work times, timesheet decisions, login times and audit entries. Confirm who may see what
   (managers: their scope; administrators: everything) and whether administrators should be able to read all time data.
5. **Approval semantics.** Does an approved timesheet have legal weight (evidence of hours worked)? Who is allowed to
   correct an approved month, and how (today nobody: it is locked)?
6. **Manager coverage.** Managers cover their direct reports and their department. Confirm that this is the wanted
   scope, including for deputies and absences of a manager (there is no delegation).
7. **Public holidays.** Confirm who maintains the regions and their holidays, and whether a history of an employee's regions is needed (a region change applies to all months).
8. **Availability of the audit CSV export.** It contains personal data; confirm who may export it and where it may be
   stored.
