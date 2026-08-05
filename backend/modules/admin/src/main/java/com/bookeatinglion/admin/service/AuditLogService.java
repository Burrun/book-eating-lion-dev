package com.bookeatinglion.admin.service;

import com.bookeatinglion.admin.domain.AuditLog;
import com.bookeatinglion.admin.repository.AuditLogRepository;
import com.bookeatinglion.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(Member admin, String action, String targetType, Long targetId,
                        String ipAddress, String details) {
        AuditLog auditLog = AuditLog.builder()
                .adminId(admin.getId())
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .ipAddress(ipAddress)
                .details(details)
                .build();
        auditLogRepository.save(auditLog);
    }
}
