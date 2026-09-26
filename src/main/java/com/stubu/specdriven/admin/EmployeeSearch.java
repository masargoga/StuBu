package com.stubu.specdriven.admin;

import com.stubu.specdriven.admin.EmployeeOverviewService.Query;
import com.stubu.specdriven.employee.EmployeeRow;
import com.stubu.specdriven.employee.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Finds employees for the administrator's list in the database: the search text, the filters, the sort order and the
 * page are all part of the query, so a list of thousands of employees costs one page of rows, not all of them. The
 * last login is read for the rows found only.
 */
@Component
public class EmployeeSearch {

    private static final String LAST_LOGIN = "(select max(a.timestamp) from AuditLogEntry a where a.userId = e.id "
            + "and a.action = com.stubu.specdriven.audit.AuditAction.LOGIN_SUCCESS)";
    private static final String MANAGER_NAME = "concat(m.firstName, ' ', m.lastName)";

    private final EntityManager entityManager;

    public EmployeeSearch(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** The employees that match the query, from {@code first} on, at most {@code max}. */
    public List<EmployeeRow> find(Query query, int first, int max) {
        Conditions conditions = conditions(query, null);
        return rows(conditions, order(query), first, max);
    }

    /** How many employees match the query. */
    public long count(Query query) {
        Conditions conditions = conditions(query, null);
        TypedQuery<Long> count = entityManager.createQuery("select count(e) from Employee e left join Department d "
                + "on d.id = e.departmentId" + conditions.where(), Long.class);
        conditions.bind(count);
        return count.getSingleResult();
    }

    /** One employee, or nothing if there is no such employee. */
    public Optional<EmployeeRow> find(long id) {
        return rows(conditions(Query.ALL, id), " order by e.id", 0, 1).stream().findFirst();
    }

    private List<EmployeeRow> rows(Conditions conditions, String order, int first, int max) {
        TypedQuery<Object[]> query = entityManager.createQuery("select e, d.name, m.firstName, m.lastName, " + LAST_LOGIN
                + ", r.name from Employee e left join Department d on d.id = e.departmentId left join Employee m "
                + "on m.id = e.managerId left join Region r on r.id = e.regionId" + conditions.where() + order,
                Object[].class);
        conditions.bind(query);
        query.setFirstResult(first);
        query.setMaxResults(max);
        return query.getResultList().stream().map(EmployeeSearch::row).toList();
    }

    private static EmployeeRow row(Object[] values) {
        var employee = (com.stubu.specdriven.employee.Employee) values[0];
        String manager = employee.getManagerId() == null || values[2] == null ? null : values[2] + " " + values[3];
        return new EmployeeRow(employee.getId(), employee.getEmail(), employee.getFirstName(), employee.getLastName(),
                employee.getRole(), employee.getDepartmentId(), (String) values[1], employee.getRegionId(),
                (String) values[5], employee.getManagerId(), manager, employee.isActive(), employee.getCreatedAt(),
                (Instant) values[4], employee.getVersion() == null ? 0 : employee.getVersion());
    }

    // --- conditions --------------------------------------------------------------------------------

    /** The where clause with its parameters. */
    private static final class Conditions {
        private final List<String> clauses = new ArrayList<>();
        private final List<Object[]> parameters = new ArrayList<>();

        void add(String clause, String name, Object value) {
            clauses.add(clause);
            parameters.add(new Object[] { name, value });
        }

        String where() {
            return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
        }

        void bind(TypedQuery<?> query) {
            parameters.forEach(parameter -> query.setParameter((String) parameter[0], parameter[1]));
        }
    }

    private static Conditions conditions(Query query, Long id) {
        Conditions conditions = new Conditions();
        if (id != null) {
            conditions.add("e.id = :id", "id", id);
        }
        String text = query.text() == null ? "" : query.text().strip().toLowerCase(Locale.ROOT);
        if (!text.isEmpty()) {
            conditions.add("(lower(e.email) like :text escape '!' or lower(concat(e.firstName, ' ', e.lastName)) "
                    + "like :text escape '!' or lower(d.name) like :text escape '!')", "text", "%" + escape(text) + "%");
        }
        if (query.active() != null) {
            conditions.add("e.active = :active", "active", query.active());
        }
        if (query.role() != null) {
            conditions.add("e.role = :role", "role", query.role());
        }
        if (query.departmentId() != null) {
            conditions.add("e.departmentId = :department", "department", query.departmentId());
        }
        return conditions;
    }

    /** Makes % and _ in the text ordinary characters ({@code !} is the escape character of the like). */
    private static String escape(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    // --- order -------------------------------------------------------------------------------------

    private static String order(Query query) {
        String direction = query.ascending() ? " asc" : " desc";
        String chosen = switch (query.sort() == null ? EmployeeOverviewService.Sort.NAME : query.sort()) {
            case NAME -> "lower(e.lastName)" + direction + ", lower(e.firstName)" + direction;
            case EMAIL -> "lower(e.email)" + direction;
            case ROLE -> "case e.role when " + role(Role.EMPLOYEE) + " then 0 when " + role(Role.MANAGER)
                    + " then 1 else 2 end" + direction;
            case DEPARTMENT -> nullsFirst("lower(d.name)", direction);
            case MANAGER -> nullsFirst("lower(" + MANAGER_NAME + ")", direction);
            case STATUS -> "case when e.active = true then 0 else 1 end" + direction; // active first
            case LAST_LOGIN -> nullsFirst(LAST_LOGIN, direction); // never signed in counts as oldest
        };
        return " order by " + chosen + ", lower(e.lastName), lower(e.firstName), e.id";
    }

    private static String role(Role role) {
        return Role.class.getName() + "." + role.name();
    }

    /** Ascending, empty values come first; descending, last. */
    private static String nullsFirst(String expression, String direction) {
        return "case when " + expression + " is null then 0 else 1 end" + direction + ", " + expression + direction;
    }
}
