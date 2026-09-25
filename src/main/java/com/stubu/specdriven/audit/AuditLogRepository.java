package com.stubu.specdriven.audit;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Deliberately exposes no update or delete operations: audit records are append-only. */
public interface AuditLogRepository extends Repository<AuditLogEntry, Long> {

    AuditLogEntry save(AuditLogEntry entry);

    List<AuditLogEntry> findAllByOrderByIdAsc();

    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, Long entityId);

    long count();

    /** For every user who signed in: the time of the latest successful login. */
    @Query("select a.userId, max(a.timestamp) from AuditLogEntry a "
            + "where a.action = com.stubu.specdriven.audit.AuditAction.LOGIN_SUCCESS and a.userId is not null "
            + "group by a.userId")
    List<Object[]> findLatestLogins();

    @Query("select distinct a.entityType from AuditLogEntry a order by a.entityType")
    List<String> findDistinctEntityTypes();
}
