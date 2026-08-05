package com.bookeatinglion.admin.repository;

import com.bookeatinglion.admin.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
