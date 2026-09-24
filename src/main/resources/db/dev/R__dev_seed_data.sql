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
