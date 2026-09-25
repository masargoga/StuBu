package com.stubu.specdriven.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stubu.specdriven.admin.EmployeeOverviewService.Query;
import com.stubu.specdriven.admin.EmployeeOverviewService.Sort;
import com.stubu.specdriven.admin.EmployeeSearch;
import com.stubu.specdriven.audit.AuditService;
import com.stubu.specdriven.employee.Department;
import com.stubu.specdriven.employee.DepartmentRepository;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.employee.Role;
import com.stubu.specdriven.timetracking.TimeEntry;
import com.stubu.specdriven.timetracking.TimeEntryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The parts of the application that depend on the database, run on a real PostgreSQL (all other tests use H2 in
 * PostgreSQL mode): the migrations including the PostgreSQL-only ones, the append-only audit log, the search, sort and
 * paging of the employee list, and optimistic locking. The tests are skipped when Docker is not available.
 * Run them alone with {@code ./mvnw -Dvaadin.skip=true test -Dtest=DatabasePostgresTest}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = { "vaadin.launch-browser=false", "vaadin.devmode.devTools.enabled=false" })
@ActiveProfiles("test")
class DatabasePostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EmployeeSearch search;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    DepartmentRepository departments;
    @Autowired
    TimeEntryRepository timeEntries;
    @Autowired
    AuditService auditService;

    private Department department() {
        return departments.save(new Department("Department " + UUID.randomUUID()));
    }

    private Employee employee(String prefix, String first, String last, Role role, Department department) {
        return employees.save(new Employee(prefix + "-" + UUID.randomUUID() + "@example.com", first, last, role,
                department.getId()));
    }

    @Test
    void everyMigrationRunsOnPostgreSql() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success and version is not null order by installed_rank",
                String.class);

        assertTrue(versions.containsAll(List.of("1", "2", "3", "4", "5", "6", "7")), "Applied: " + versions);
        assertEquals(0, jdbc.queryForObject("select count(*) from flyway_schema_history where not success",
                Integer.class));
    }

    @Test
    void theDatabaseKeepsTheAuditLogAppendOnly() {
        Employee someone = employee("audit", "Audit", "Subject", Role.EMPLOYEE, department());
        auditService.recordLoginSuccess(someone.getId());
        assertTrue(jdbc.queryForObject("select count(*) from audit_log", Integer.class) > 0);

        assertThrows(DataAccessException.class, () -> jdbc.update("update audit_log set reason = 'rewritten'"));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from audit_log"));
        assertThrows(DataAccessException.class, () -> jdbc.execute("truncate table audit_log"));
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_log where reason = 'rewritten'", Integer.class));
    }

    @Test
    void theEmployeeListIsSearchedSortedAndPagedInTheDatabase() {
        Department only = department();
        String surname = "Pg" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 1; i <= 30; i++) {
            employee("list", "First%02d".formatted(i), surname, i % 5 == 0 ? Role.MANAGER : Role.EMPLOYEE, only);
        }
        Query mine = new Query(surname, null, null, only.getId(), Sort.EMAIL, true);

        assertEquals(30, search.count(mine));
        List<EmployeeRow> first = search.find(mine, 0, 25);
        List<EmployeeRow> rest = search.find(mine, 25, 25);
        assertEquals(25, first.size());
        assertEquals(5, rest.size());
        assertEquals(only.getName(), first.get(0).departmentName());
        assertEquals(30, search.count(new Query(surname.toUpperCase(), null, null, null, Sort.NAME, true)),
                "Not case-sensitive");
        assertEquals(0, search.count(new Query("%", null, null, only.getId(), Sort.NAME, true)),
                "A lone % is an ordinary character");
    }

    @Test
    void everySortOrderRunsOnPostgreSql() {
        Employee manager = employee("sort", "Sort", "Manager", Role.MANAGER, department());
        Employee report = employee("sort", "Sort", "Report", Role.EMPLOYEE, department());
        report.setManagerId(manager.getId());
        employees.save(report);
        auditService.recordLoginSuccess(report.getId());
        long total = search.count(Query.ALL);

        for (Sort sort : Sort.values()) {
            for (boolean ascending : new boolean[] { true, false }) {
                List<EmployeeRow> rows = search.find(new Query(null, null, null, null, sort, ascending), 0,
                        Integer.MAX_VALUE);
                assertEquals(total, rows.size(), sort + " " + ascending);
            }
        }
        EmployeeRow row = search.find(report.getId()).orElseThrow();
        assertNotNull(row.lastLoginAt(), "The last login comes from the audit log");
        assertEquals(manager.getFullName(), row.managerName());
    }

    @Test
    void aSaveBasedOnAnOutdatedVersionIsRefused() {
        Employee created = employee("lock", "Lock", "Subject", Role.EMPLOYEE, department());
        Employee first = employees.findById(created.getId()).orElseThrow();
        Employee second = employees.findById(created.getId()).orElseThrow();

        first.setFirstName("Changed");
        employees.saveAndFlush(first);
        second.setFirstName("Overwritten");

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> employees.saveAndFlush(second));
        assertEquals("Changed", employees.findById(created.getId()).orElseThrow().getFirstName());
    }

    @Test
    void momentsKeepTheirValueAndOnlyOneWorkPeriodCanBeOpen() {
        Employee worker = employee("time", "Time", "Worker", Role.EMPLOYEE, department());
        Instant in = Instant.parse("2026-03-29T00:30:00Z"); // around the change to summer time
        TimeEntry stored = timeEntries.saveAndFlush(TimeEntry.open(worker.getId(), in));

        assertEquals(in, timeEntries.findById(stored.getId()).orElseThrow().getCheckInAt());
        assertThrows(DataAccessException.class,
                () -> timeEntries.saveAndFlush(TimeEntry.open(worker.getId(), in.plusSeconds(60))));
        assertFalse(timeEntries.findByOpenEmployeeId(worker.getId()).isEmpty());
    }
}
