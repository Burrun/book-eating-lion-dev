package com.bookeatinglion.admin.repository;

import com.bookeatinglion.admin.AdminModuleTestApplication;
import com.bookeatinglion.admin.domain.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = AdminModuleTestApplication.class)
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.save(auditLog("ORDER_CANCELLED"));
        auditLogRepository.save(auditLog("MEMBER_SUSPENDED"));
    }

    private AuditLog auditLog(String action) {
        return AuditLog.builder()
                .adminId(1L)
                .action(action)
                .targetType("ORDER")
                .targetId(100L)
                .ipAddress("127.0.0.1")
                .details("상세 내역")
                .build();
    }

    @Test
    void 감사로그를_페이지로_조회한다() {
        Page<AuditLog> result = auditLogRepository.findAll(PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(AuditLog::getAction)
                .containsExactlyInAnyOrder("ORDER_CANCELLED", "MEMBER_SUSPENDED");
    }
}
