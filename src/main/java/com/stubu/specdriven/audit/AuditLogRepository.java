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

    /** The time of the user's latest successful login, or {@code null} if they never signed in. */
    @Query("select max(a.timestamp) from AuditLogEntry a where a.userId = :userId "
            + "and a.action = com.stubu.specdriven.audit.AuditAction.LOGIN_SUCCESS")
    java.time.Instant findLastLogin(@org.springframework.data.repository.query.Param("userId") Long userId);

    @Query("select distinct a.entityType from AuditLogEntry a order by a.entityType")
    List<String> findDistinctEntityTypes();
}
