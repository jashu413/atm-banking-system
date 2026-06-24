package com.bank.service;

import com.bank.domain.AuditAction;
import com.bank.domain.AuditLog;
import com.bank.domain.AuditStatus;
import com.bank.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists audit events without recording passwords, PINs, or JWT tokens. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(AuditAction action, String username, String accountNumber,
                        String targetAccountNumber, String message) {
        record(action, AuditStatus.SUCCESS, username, accountNumber, targetAccountNumber, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(AuditAction action, String username, String accountNumber,
                        String targetAccountNumber, String message) {
        record(action, AuditStatus.FAILURE, username, accountNumber, targetAccountNumber, message);
    }

    private void record(AuditAction action, AuditStatus status, String username,
                        String accountNumber, String targetAccountNumber, String message) {
        auditLogRepository.save(new AuditLog(
                action,
                status,
                sanitize(username),
                sanitize(accountNumber),
                sanitize(targetAccountNumber),
                sanitizeMessage(message)));
        log.info("Audit event recorded: action={}, status={}, username={}, accountNumber={}, targetAccountNumber={}",
                action, status, username, accountNumber, targetAccountNumber);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private String sanitizeMessage(String message) {
        String cleaned = sanitize(message);
        if (cleaned == null) {
            return null;
        }
        return cleaned.length() <= 255 ? cleaned : cleaned.substring(0, 255);
    }
}
