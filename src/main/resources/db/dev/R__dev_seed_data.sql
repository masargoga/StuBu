-- Development-only seed data (Flyway location classpath:db/dev, enabled by the "dev" profile).
-- Log in with the mock identity provider using one of these email addresses.

INSERT INTO department (name, created_at) VALUES ('Engineering', CURRENT_TIMESTAMP);
INSERT INTO department (name, created_at) VALUES ('Human Resources', CURRENT_TIMESTAMP);

INSERT INTO employee (email, first_name, last_name, role, manager_id, department_id, is_active, created_at, updated_at)
SELECT 'carol.admin@example.com', 'Carol', 'Admin', 'ADMIN', NULL, d.id, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM department d WHERE d.name = 'Human Resources';

INSERT INTO employee (email, first_name, last_name, role, manager_id, department_id, is_active, created_at, updated_at)
SELECT 'bob.manager@example.com', 'Bob', 'Manager', 'MANAGER', c.id, d.id, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM department d, employee c WHERE d.name = 'Engineering' AND c.email = 'carol.admin@example.com';

INSERT INTO employee (email, first_name, last_name, role, manager_id, department_id, is_active, created_at, updated_at)
SELECT 'alice.employee@example.com', 'Alice', 'Employee', 'EMPLOYEE', b.id, d.id, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM department d, employee b WHERE d.name = 'Engineering' AND b.email = 'bob.manager@example.com';

INSERT INTO employee (email, first_name, last_name, role, manager_id, department_id, is_active, created_at, updated_at)
SELECT 'dave.inactive@example.com', 'Dave', 'Inactive', 'EMPLOYEE', b.id, d.id, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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
INSERT INTO public_holiday (holiday_date, name, created_at)
VALUES (DATEADD('DAY', -3, CURRENT_DATE), 'Company Day', CURRENT_TIMESTAMP);

-- Alice also worked three days last month, so that her timesheet for that (finished) month can be submitted.
INSERT INTO time_entry (employee_id, check_in_at, check_out_at, open_employee_id, version, created_at, updated_at)
SELECT e.id,
       DATEADD('HOUR', 9, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -1, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       DATEADD('HOUR', 17, CAST(DATEADD('DAY', r.x, DATEADD('MONTH', -1, DATE_TRUNC('MONTH', CURRENT_DATE))) AS TIMESTAMP WITH TIME ZONE)),
       NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM employee e, system_range(7, 9) AS r(x) WHERE e.email = 'alice.employee@example.com';
