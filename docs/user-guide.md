# User guide

The main workflows of StuBu from the point of view of the people who use it. The application is available in English
and German (it follows the language of your browser), works on phones, tablets and desktops, and uses large,
high-contrast controls.

## Signing in

Open the address of the application and choose **Sign in with …** (Microsoft or Google, depending on what your
company configured). You need an **active employee record with the same email address** as your account. If you see
"not registered" or "deactivated", ask an administrator. **Sign out** is in the header.

If an administrator deactivates you or changes your role while you are signed in, you are sent back to the login page
within a few seconds and sign in again.

## Employees

### Recording your time (page "Today")

* **Check In** starts a work period at the current server time; **Check Out** ends it. The timeline shows today's
  periods, the time worked and the break time (the gaps between periods).
* Already checked in and press Check In again? You are asked whether to **replace** the check-in time.
* Forgot to check in? Press Check Out and enter **when you started**.
* Note that the times come from the server, not from your device's clock.
* The top card shows whether you are working, the current time and how long you have been working; the tiles below it
  show the total, the break and your first check-in of the day. "Your day" draws each work period as a green block
  (striped while it is still open) between 6:00 and 20:00; the scale widens when you worked earlier or later.

### Correcting earlier days

On "Today" and in "My Timesheet" every work period has **Edit** and **Delete**. Give a reason if you like; it is kept in
the audit log. Periods cannot overlap. You can correct a day only while the month is **not submitted** or after it was
**rejected**; a submitted or approved month is locked.

### Your month ("My Timesheet")

Shows every day of the month with weekends and public holidays marked, the worked time, the break time and the total.
Switch between **Timeline** and **Table**. The badge shows the state: *Not submitted*, *Submitted*, *Approved*,
*Rejected* (with the reason and who decided).

### Submitting

When the month is over, **Submit timesheet** is enabled (before that it says from when you can submit). You need at
least one work period, and none may still be open. After you confirm, your manager is emailed and you cannot change the
month until it is decided.

If your manager **rejects** it, you see the reason, correct the days and submit again; the manager is told it is a
resubmission.

## Managers

* **Approvals** lists the submitted timesheets of your direct reports and your department. **Review**
  opens one with the days, totals, its history (earlier submissions and decisions with comments) and single days.
* **Approve timesheet** asks for confirmation. **Reject timesheet** needs a **reason**, which the employee sees.
  Either way the employee gets an email. If somebody else decided in the meantime you are told the current state.
* You cannot decide on your own timesheet.
* **Employees** lists the people of your scope; choose **Direct reports only** or **All department employees**. Open
  an employee's month (any state, read-only) to see the timeline and history.

## Administrators

* **Employees** (management): all employees, active and inactive, with role, manager, department, status and last
  login. Search by name, email or department, filter by status, role and department, sort by any column, 25 per page.
  **Add employee**, **Edit** (the email cannot change) and **Deactivate** (with an optional reason; not yourself and not
  the last administrator). **Details** shows the whole record and all timesheets of the person; **Open** shows a month
  read-only. Nothing is ever deleted.
  If two administrators edit the same person at once, the second save is refused with a message; look at the current
  data and try again.
* **Public holidays**: add, edit and delete holidays; they mark days in the month views and never change worked time.
* **Audit log**: newest first, 25 per page. Filter by date range, user, kind of record and action; open an entry to see
  old and new values; **Export as CSV** exports what the filter shows.
* Administrators can look at every employee's timesheet but **do not approve or reject** timesheets; that is the
  manager's job.

## When something goes wrong

* "Unable to load data. Please try again." with a **Retry** button: the database was not reachable; retry in a moment.
* "This … was changed by someone else": somebody saved before you; reopen the record and repeat your change.
* A page you may not open shows a plain "no permission" message.
