package com.stubu.specdriven.approval;

import com.stubu.specdriven.audit.AuditAction;
import java.time.Instant;

/**
 * One step in the life of a timesheet, taken from the audit log.
 *
 * @param actorName who did it, or {@code null} if that is not known
 * @param reason    e.g. the rejection reason; may be {@code null}
 */
public record HistoryEntry(Instant at, AuditAction action, String actorName, String reason) {
}
