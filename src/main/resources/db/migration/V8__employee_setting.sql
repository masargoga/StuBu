-- UC-016: personal settings of an employee, starting with the language of the application. One row per employee,
-- created the first time the employee saves a setting; one column per setting, empty meaning "not chosen, use the
-- default". Further settings (for example a theme) are added as further nullable columns. Portable SQL: runs
-- unchanged on PostgreSQL and H2.

CREATE TABLE employee_setting (
    employee_id BIGINT                   NOT NULL PRIMARY KEY,
    language    VARCHAR(5),
    version     BIGINT                   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_employee_setting_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT ck_employee_setting_language CHECK (language IS NULL OR language IN ('en', 'de', 'es', 'fr'))
);
