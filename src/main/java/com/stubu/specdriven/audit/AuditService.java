package com.stubu.specdriven.audit;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    static final String EMPLOYEE = "Employee";
    private static final int MAX_REASON_LENGTH = 1000;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Records a general audit event. The timestamp always comes from the server. */
    @Transactional
    public AuditLogEntry record(Long userId, String entityType, Long entityId, AuditAction action,
            String oldValues, String newValues, String reason) {
        return repository.save(new AuditLogEntry(clock.instant(), userId, entityType, entityId, action, oldValues,
                newValues, truncate(reason)));
    }

    public AuditLogEntry recordLoginSuccess(Long employeeId) {
        return record(employeeId, EMPLOYEE, employeeId, AuditAction.LOGIN_SUCCESS, null, null, null);
    }

    /**
     * Records a failed login attempt.
     *
     * @param employeeId the matching employee, or {@code null} when none could be identified
     * @param reason     what went wrong, including the attempted email address where known
     */
    public AuditLogEntry recordLoginFailure(Long employeeId, String reason) {
        return record(employeeId, EMPLOYEE, employeeId, AuditAction.LOGIN_FAILURE, null, null, reason);
    }

    private static String truncate(String value) {
        return value != null && value.length() > MAX_REASON_LENGTH ? value.substring(0, MAX_REASON_LENGTH) : value;
    }
}
