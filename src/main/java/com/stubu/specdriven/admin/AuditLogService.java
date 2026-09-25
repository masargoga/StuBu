package com.stubu.specdriven.admin;

import com.stubu.specdriven.audit.AuditAction;
import com.stubu.specdriven.audit.AuditLogEntry;
import com.stubu.specdriven.audit.AuditLogRepository;
import com.stubu.specdriven.employee.AdminAccess;
import com.stubu.specdriven.employee.Employee;
import com.stubu.specdriven.employee.EmployeeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the audit log for administrators (UC-013): filtered, newest first, page by page, and as a CSV export. It
 * only reads; the audit log is append-only and there is no way to change or delete an entry here or anywhere else.
 */
@Service
public class AuditLogService {

    /** The page size of the list. */
    public static final int PAGE_SIZE = 25;
    /** A CSV export stops here; the export says so when it did. */
    public static final int MAX_EXPORT_ROWS = 10_000;

    private static final String SYSTEM = "system";

    private final AuditLogRepository auditLog;
    private final EmployeeRepository employees;
    private final AdminAccess adminAccess;
    private final EntityManager entityManager;

    public AuditLogService(AuditLogRepository auditLog, EmployeeRepository employees, AdminAccess adminAccess,
            EntityManager entityManager) {
        this.auditLog = auditLog;
        this.employees = employees;
        this.adminAccess = adminAccess;
        this.entityManager = entityManager;
    }

    /**
     * What to look for; every part is optional.
     *
     * @param from       first day, inclusive, in the time zone of the search
     * @param to         last day, inclusive
     * @param user       text in the name or email address of the acting user; "system" finds automatic entries
     * @param entityType e.g. "Employee" or "Timesheet"
     */
    public record Filter(LocalDate from, LocalDate to, String user, String entityType, AuditAction action) {

        public static final Filter NONE = new Filter(null, null, null, null, null);

        public boolean isEmpty() {
            return from == null && to == null && (user == null || user.isBlank()) && (entityType == null
                    || entityType.isBlank()) && action == null;
        }
    }

    /** An entry with the acting user resolved and a one-line summary of what changed. */
    public record Row(long id, Instant timestamp, Long userId, String userName, String userEmail, String entityType,
            Long entityId, AuditAction action, String reason, String oldValues, String newValues, String summary) {

        /** Automatic entries have no user. */
        public boolean isSystem() {
            return userId == null;
        }
    }

    /** One page of results and how many entries match in all. */
    public record Result(List<Row> rows, long total, int page, int pages) {
    }

    /** Whether the log has any entries at all, whatever the filter. */
    @Transactional(readOnly = true)
    public boolean hasEntries(long adminId) {
        adminAccess.require(adminId);
        return auditLog.count() > 0;
    }

    /** The entity types that occur in the log, for the filter. */
    @Transactional(readOnly = true)
    public List<String> entityTypes(long adminId) {
        adminAccess.require(adminId);
        return auditLog.findDistinctEntityTypes();
    }

    /**
     * Searches the log, newest entry first.
     *
     * @param page starting at 0
     * @throws com.stubu.specdriven.employee.AdminOnlyException if the acting user is not an active administrator
     */
    @Transactional(readOnly = true)
    public Result search(long adminId, Filter filter, ZoneId zone, int page) {
        adminAccess.require(adminId);
        int pageNumber = Math.max(page, 0);
        long total = count(filter, zone);
        int pages = (int) Math.max((total + PAGE_SIZE - 1) / PAGE_SIZE, 1);
        return new Result(rows(find(filter, zone, pageNumber * PAGE_SIZE, PAGE_SIZE)), total, pageNumber, pages);
    }

    /**
     * The matching entries, newest first, as CSV (UTF-8 with a byte order mark so spreadsheets read it right; cells
     * that could be taken for a formula are marked as text). At most {@link #MAX_EXPORT_ROWS} rows.
     */
    @Transactional(readOnly = true)
    public String exportCsv(long adminId, Filter filter, ZoneId zone) {
        adminAccess.require(adminId);
        List<AuditLogEntry> entries = find(filter, zone, 0, MAX_EXPORT_ROWS);
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("timestamp,user,user email,entity type,entity id,action,reason,old values,new values\r\n");
        for (Row row : rows(entries)) {
            csv.append(String.join(",", cell(row.timestamp().toString()), cell(row.isSystem() ? "System" : row.userName()),
                    cell(row.userEmail()), cell(row.entityType()), cell(row.entityId() == null ? "" : row.entityId().toString()),
                    cell(row.action().name()), cell(row.reason()), cell(row.oldValues()), cell(row.newValues())))
                    .append("\r\n");
        }
        return csv.toString();
    }

    private long count(Filter filter, ZoneId zone) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<AuditLogEntry> root = query.from(AuditLogEntry.class);
        query.select(builder.count(root)).where(predicates(builder, root, filter, zone));
        return entityManager.createQuery(query).getSingleResult();
    }

    /** The matching entries, newest first; {@code first} is the number of entries to skip. */
    private List<AuditLogEntry> find(Filter filter, ZoneId zone, int first, int max) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditLogEntry> query = builder.createQuery(AuditLogEntry.class);
        Root<AuditLogEntry> root = query.from(AuditLogEntry.class);
        query.select(root).where(predicates(builder, root, filter, zone))
                .orderBy(builder.desc(root.get("timestamp")), builder.desc(root.get("id")));
        return entityManager.createQuery(query).setFirstResult(first).setMaxResults(max).getResultList();
    }

    private Predicate[] predicates(CriteriaBuilder builder, Root<AuditLogEntry> root, Filter filter, ZoneId zone) {
        String text = filter.user() == null ? "" : filter.user().strip().toLowerCase(Locale.ROOT);
        Set<Long> userIds = text.isEmpty() ? Set.of()
                : Set.copyOf(employees.findIdsByText("%" + escapeLike(text) + "%"));
        List<Predicate> all = new ArrayList<>();
        if (filter.from() != null) {
            all.add(builder.greaterThanOrEqualTo(root.<Instant>get("timestamp"),
                    filter.from().atStartOfDay(zone).toInstant()));
        }
        if (filter.to() != null) {
            all.add(builder.lessThan(root.<Instant>get("timestamp"),
                    filter.to().plusDays(1).atStartOfDay(zone).toInstant()));
        }
        if (filter.entityType() != null && !filter.entityType().isBlank()) {
            all.add(builder.equal(root.get("entityType"), filter.entityType()));
        }
        if (filter.action() != null) {
            all.add(builder.equal(root.get("action"), filter.action()));
        }
        if (!text.isEmpty()) {
            List<Predicate> whoever = new ArrayList<>();
            if (!userIds.isEmpty()) {
                whoever.add(root.get("userId").in(userIds));
            }
            if (text.equals(SYSTEM)) {
                whoever.add(builder.isNull(root.get("userId")));
            }
            all.add(whoever.isEmpty() ? builder.disjunction() : builder.or(whoever.toArray(new Predicate[0])));
        }
        return all.toArray(new Predicate[0]);
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private List<Row> rows(List<AuditLogEntry> entries) {
        Set<Long> ids = entries.stream().map(AuditLogEntry::getUserId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Employee> users = employees.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        return entries.stream().map(entry -> {
            Employee user = entry.getUserId() == null ? null : users.get(entry.getUserId());
            return new Row(entry.getId(), entry.getTimestamp(), entry.getUserId(),
                    user == null ? null : user.getFullName(), user == null ? null : user.getEmail(),
                    entry.getEntityType(), entry.getEntityId(), entry.getAction(), entry.getReason(),
                    entry.getOldValues(), entry.getNewValues(), AuditSummary.of(entry.getOldValues(),
                            entry.getNewValues()));
        }).toList();
    }

    /** A CSV cell in quotes; text that a spreadsheet would read as a formula gets a leading apostrophe. */
    static String cell(String text) {
        String value = text == null ? "" : text;
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
