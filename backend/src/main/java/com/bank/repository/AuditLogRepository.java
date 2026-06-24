package com.bank.repository;

import com.bank.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository for persisted audit records. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUsernameOrderByCreatedAtDescIdDesc(String username);
}
