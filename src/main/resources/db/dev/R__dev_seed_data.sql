-- Development-only seed data (Flyway location classpath:db/dev, enabled by the "dev" profile).
-- Log in with the mock identity provider using one of these email addresses.

INSERT INTO department (name, created_at) VALUES ('Engineering', CURRENT_TIMESTAMP);
INSERT INTO department (name, created_at) VALUES ('Human Resources', CURRENT_TIMESTAMP);

INSERT INTO employee (email, first_name, last_name, role, manager_id, department_id, region_id, is_active, created_at, updated_at)
SELECT 'carol.admin@example.com', 'Carol', 'Admin', 'ADMIN', NULL, d.id, (SELECT id FROM region WHERE name = 'Default'), TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM department d WHERE d.name = 'Human Resources';

INSERT INTO employee (email, first_name, last_name, role, manager_id, department_id, region_id, is_active, created_at, updated_at)
SELECT 'bob.manager@example.com', 'Bob', 'Manager', 'MANAGER', c.id, d.id, (SELECT id FROM region WHERE name = 'Default'), TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM department d, employee c WHERE d.name = 'Engineering' AND c.email = 'carol.admin@example.com';

INSERT INTO employee (email, first_name, last_name, role, manager_id, department_id, region_id, is_active, created_at, updated_at)
SELECT 'alice.employee@example.com', 'Alice', 'Employee', 'EMPLOYEE', b.id, d.id, (SELECT id FROM region WHERE name = 'Default'), TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM department d, employee b WHERE d.name = 'Engineering' AND b.email = 'bob.manager@example.com';

INSERT INTO employee (email, first_name, last_name, role, manager_id, department_id, region_id, is_active, created_at, updated_at)
SELECT 'dave.inactive@example.com', 'Dave', 'Inactive', 'EMPLOYEE', b.id, d.id, (SELECT id FROM region WHERE name = 'Default'), FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM department d, employee b WHERE d.name = 'Engineering' AND b.email = 'bob.manager@example.com';

-- Alice worked the last eight days (morning and afternoon, with a lunch break) so the timesheet views have
-- something to show. Dates are relative to the day the application starts.
INSERT INTO time_entry (employee_id, check_in_at, check_out_at, open_employee_id, version, created_at, updated_at)
SELECT e.id,
       DATEADD('HOUR', 8, CAST(DATEADD('DAY', -r.x, CURRENT_DATE) AS TIMESTAMP WITH TIME ZONE)),
       DATEADD('HOUR', 12, CAST(DATEADD('DAY', -r.x, CURRENT_DATE) AS TIMESTAMP WITH TIME ZONE)),
       NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM employee e, system_range(1, 8) AS r(x) WHERE e.email = 'alice.employee@example.com';

INSERT INTO time_entry (employee_id, check_in_at, check_out_at, open_employee_id, version, created_at, updated_at)
SELECT e.id,
       DATEADD('MINUTE', 750, CAST(DATEADD('DAY', -r.x, CURRENT_DATE) AS TIMESTAMP WITH TIME ZONE)),
       DATEADD('MINUTE', 1050, CAST(DATEADD('DAY', -r.x, CURRENT_DATE) AS TIMESTAMP WITH TIME ZONE)),
       NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM employee e, system_range(1, 8) AS r(x) WHERE e.email = 'alice.employee@example.com';

-- A public holiday three days ago (management of holidays is UC-012).
INSERT INTO public_holiday (region_id, holiday_date, name, created_at)
VALUES ((SELECT id FROM region WHERE name = 'Default'), DATEADD('DAY', -3, CURRENT_DATE), 'Company Day', CURRENT_TIMESTAMP);

-- Alice also worked three days last month, so that her timesheet for that (finished) month can be submitted.
INSERT INTO time_entry (employee_id, check_in_at, check_out_at, open_employee_id, version, created_at, updated_at)
SELECT e.id,
       DATEADD('HOUR', 9, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -1, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       DATEADD('HOUR', 17, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -1, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM employee e, system_range(7, 9) AS r(x) WHERE e.email = 'alice.employee@example.com';

-- Erik works in the region USA, reports to Bob and has submitted last month's timesheet, so Bob has something to
-- review, with the holidays of Erik's region (not Bob's).
INSERT INTO region (name, version, created_at, updated_at) VALUES ('USA', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO public_holiday (region_id, holiday_date, name, created_at)
SELECT r.id, CAST(DATEADD('DAY', 3, DATEADD('MONTH', -1, DATE_TRUNC('MONTH', CURRENT_DATE))) AS DATE), 'Independence Day (USA)', CURRENT_TIMESTAMP
FROM region r WHERE r.name = 'USA';

INSERT INTO employee (email, first_name, last_name, role, manager_id, department_id, region_id, is_active, created_at, updated_at)
SELECT 'erik.employee@example.com', 'Erik', 'Employee', 'EMPLOYEE', b.id, d.id, (SELECT id FROM region WHERE name = 'USA'), TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM department d, employee b WHERE d.name = 'Engineering' AND b.email = 'bob.manager@example.com';

INSERT INTO time_entry (employee_id, check_in_at, check_out_at, open_employee_id, version, created_at, updated_at)
SELECT e.id,
       DATEADD('HOUR', 8, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -1, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       DATEADD('HOUR', 12, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -1, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM employee e, system_range(2, 6) AS r(x) WHERE e.email = 'erik.employee@example.com';

INSERT INTO time_entry (employee_id, check_in_at, check_out_at, open_employee_id, version, created_at, updated_at)
SELECT e.id,
       DATEADD('MINUTE', 750, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -1, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       DATEADD('MINUTE', 1050, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -1, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM employee e, system_range(2, 6) AS r(x) WHERE e.email = 'erik.employee@example.com';

INSERT INTO timesheet (employee_id, period_year, period_month, status, submitted_at, version, created_at, updated_at)
SELECT e.id, YEAR(DATEADD('MONTH', -1, CURRENT_DATE)), MONTH(DATEADD('MONTH', -1, CURRENT_DATE)), 'SUBMITTED',
       CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM employee e WHERE e.email = 'erik.employee@example.com';

-- Two months ago Alice worked three days and Bob rejected her timesheet, so it can be corrected and resubmitted.
INSERT INTO time_entry (employee_id, check_in_at, check_out_at, open_employee_id, version, created_at, updated_at)
SELECT e.id,
       DATEADD('HOUR', 9, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -2, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       DATEADD('HOUR', 18, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -2, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM employee e, system_range(14, 16) AS r(x) WHERE e.email = 'alice.employee@example.com';

INSERT INTO timesheet (employee_id, period_year, period_month, status, submitted_at, rejected_at, rejected_by,
                       rejection_reason, version, created_at, updated_at)
SELECT a.id, YEAR(DATEADD('MONTH', -2, CURRENT_DATE)), MONTH(DATEADD('MONTH', -2, CURRENT_DATE)), 'REJECTED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, b.id, 'The hours on the second day look too long, please check them.',
       0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM employee a, employee b WHERE a.email = 'alice.employee@example.com' AND b.email = 'bob.manager@example.com';
