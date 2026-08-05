package com.bookeatinglion.admin.dto;

import com.bookeatinglion.admin.domain.AuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long adminId,
        String action,
        String targetType,
        Long targetId,
        String ipAddress,
        String details,
        LocalDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getAdminId(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getIpAddress(),
                auditLog.getDetails(),
                auditLog.getCreatedAt()
        );
    }
}
