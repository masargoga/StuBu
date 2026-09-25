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

    @Query("select distinct a.entityType from AuditLogEntry a order by a.entityType")
    List<String> findDistinctEntityTypes();
}
