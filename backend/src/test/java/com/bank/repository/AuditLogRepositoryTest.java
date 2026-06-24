package com.bank.repository;

import com.bank.domain.AuditAction;
import com.bank.domain.AuditLog;
import com.bank.domain.AuditStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void savesAndReadsAuditLogWithoutSensitivePayloads() {
        AuditLog saved = auditLogRepository.save(new AuditLog(
                AuditAction.TRANSFER,
                AuditStatus.SUCCESS,
                "asha",
                "1001001001",
                "1001001002",
                "Transfer completed."));

        List<AuditLog> logs = auditLogRepository.findByUsernameOrderByCreatedAtDescIdDesc("asha");

        assertThat(saved.getId()).isNotNull();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AuditAction.TRANSFER);
        assertThat(logs.get(0).getStatus()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(logs.get(0).getMessage()).doesNotContain("1234", "Password", "Bearer");
    }
}
