-- =====================================================================
-- Transaction Processor Database Schema
-- =====================================================================

CREATE DATABASE IF NOT EXISTS transaction_processor;
USE transaction_processor;

-- Account balances
CREATE TABLE IF NOT EXISTS account_balances (
    account_id   VARCHAR(50)  NOT NULL PRIMARY KEY,
    balance      DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    currency     VARCHAR(3)    NOT NULL DEFAULT 'USD',
    version      BIGINT        NOT NULL DEFAULT 0
) ENGINE=InnoDB;

-- Transaction requests
CREATE TABLE IF NOT EXISTS transaction_requests (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_id  VARCHAR(100)   NOT NULL,
    request_id      VARCHAR(100)   NOT NULL,
    account_id      VARCHAR(50)    NOT NULL,
    transaction_type VARCHAR(20)   NOT NULL,
    amount          DECIMAL(15,2)  NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    sequence_number INT            DEFAULT NULL,
    event_time      DATETIME       DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20)    NOT NULL DEFAULT 'RECEIVED',
    retry_count     INT            NOT NULL DEFAULT 0,
    max_retries     INT            NOT NULL DEFAULT 3,
    error_message   VARCHAR(500)   DEFAULT NULL,
    created_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME       DEFAULT NULL,

    CONSTRAINT uk_transaction_id UNIQUE (transaction_id),
    CONSTRAINT uk_request_id     UNIQUE (request_id),
    INDEX idx_status (status),
    INDEX idx_account_seq (account_id, sequence_number)
) ENGINE=InnoDB;

-- Processing audit log
CREATE TABLE IF NOT EXISTS processing_log (
    id              BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_id  VARCHAR(100)   NOT NULL,
    request_id      VARCHAR(100)   NOT NULL,
    action          VARCHAR(50)    NOT NULL,
    result          VARCHAR(500)   DEFAULT NULL,
    logged_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_txn_id (transaction_id)
) ENGINE=InnoDB;
