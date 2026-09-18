package com.exelynt.booking.audit.repository;

import java.util.List;

import com.exelynt.booking.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByActorOrderByIdDesc(String actor);

    List<AuditLog> findByActionOrderByIdDesc(String action);
}
