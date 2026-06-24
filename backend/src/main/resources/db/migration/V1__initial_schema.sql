-- ─────────────────────────────────────────────────────────────────────────────
-- V1__initial_schema.sql
-- Baseline schema for the ATM Banking System.
-- Mirrors the JPA entity model (Hibernate 6 + MySQLDialect).
-- ─────────────────────────────────────────────────────────────────────────────

-- users  (UserAccount entity)
CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    username              VARCHAR(50)  NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    email                 VARCHAR(120),
    email_verified        BIT(1)       NOT NULL DEFAULT 0,
    enabled               BIT(1)       NOT NULL DEFAULT 1,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    account_locked        BIT(1)       NOT NULL DEFAULT 0,
    created_at            DATETIME(6),
    updated_at            DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
);

-- customers
CREATE TABLE customers (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    customer_code VARCHAR(20)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(120),
    phone         VARCHAR(20),
    user_id       BIGINT,
    created_at    DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_customer_code (customer_code),
    UNIQUE KEY uk_customers_user_id (user_id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- accounts  (SINGLE_TABLE inheritance: discriminator = account_type)
CREATE TABLE accounts (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    account_type           VARCHAR(20)    NOT NULL,
    account_number         VARCHAR(20)    NOT NULL,
    pin_hash               VARCHAR(100)   NOT NULL,
    balance                DECIMAL(19, 2) NOT NULL,
    daily_withdrawal_limit DECIMAL(19, 2) NOT NULL,
    locked                 BIT(1)         NOT NULL DEFAULT 0,
    version                BIGINT,
    customer_id            BIGINT         NOT NULL,
    created_at             DATETIME(6),
    updated_at             DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_accounts_account_number (account_number),
    UNIQUE KEY uk_accounts_customer_id (customer_id),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

-- transactions
CREATE TABLE transactions (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    account_id      BIGINT         NOT NULL,
    type            VARCHAR(20)    NOT NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    balance_after   DECIMAL(19, 2) NOT NULL,
    description     VARCHAR(255),
    related_account VARCHAR(20),
    created_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_txn_account    (account_id),
    INDEX idx_txn_created_at (created_at),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

-- audit_logs
CREATE TABLE audit_logs (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    action                VARCHAR(30) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    username              VARCHAR(50),
    account_number        VARCHAR(20),
    target_account_number VARCHAR(20),
    message               VARCHAR(255),
    created_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_action     (action),
    INDEX idx_audit_username   (username),
    INDEX idx_audit_created_at (created_at)
);

-- refresh_tokens
CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash  VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    session_id  VARCHAR(36)  NOT NULL,
    device_info VARCHAR(512),
    ip_address  VARCHAR(45),
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6),
    revoked_at  DATETIME(6),
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rt_token_hash (token_hash),
    INDEX idx_rt_token_hash (token_hash),
    INDEX idx_rt_user_id    (user_id),
    INDEX idx_rt_session_id (session_id),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- password_reset_tokens
CREATE TABLE password_reset_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    token_hash VARCHAR(64) NOT NULL,
    user_id    BIGINT      NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_prt_token_hash (token_hash),
    INDEX idx_prt_token_hash (token_hash),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- email_verification_tokens
CREATE TABLE email_verification_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash  VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    email       VARCHAR(120) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    verified_at DATETIME(6),
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evt_token_hash (token_hash),
    INDEX idx_evt_token_hash (token_hash),
    CONSTRAINT fk_evt_user FOREIGN KEY (user_id) REFERENCES users (id)
);
