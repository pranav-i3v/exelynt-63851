package com.exelynt.booking.audit;

import com.exelynt.booking.common.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit rows. Runs in its own transaction so an audit entry survives the
 * rollback of the business transaction that produced it (a failed login, for
 * instance, must still be recorded).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, AuditAction action, String entityType, Object entityId) {
        String resolvedActor = (actor == null || actor.isBlank()) ? "anonymous" : actor;
        String resolvedEntityId = entityId == null ? null : String.valueOf(entityId);
        auditLogRepository.save(new AuditLog(
                resolvedActor, action, entityType, resolvedEntityId, CorrelationIdFilter.current()));
        log.info("audit action={} actor={} entityType={} entityId={}",
                action, resolvedActor, entityType, resolvedEntityId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, AuditAction action) {
        record(actor, action, null, null);
    }
}
