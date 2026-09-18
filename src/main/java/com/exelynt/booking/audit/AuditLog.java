package com.exelynt.booking.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Immutable record of a security- or data-relevant action. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Username of whoever triggered the action, or {@code anonymous}. */
    @Column(name = "actor", length = 50)
    private String actor;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 40)
    private String entityId;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    protected AuditLog() {
        // for JPA
    }

    public AuditLog(String actor, AuditAction action, String entityType, String entityId, String correlationId) {
        this.actor = actor;
        this.action = action.name();
        this.entityType = entityType;
        this.entityId = entityId;
        this.correlationId = correlationId;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
