-- V1__create_ledger_and_transaction_tables.sql
-- ROKAD -- Daily Ledger: initial schema (SRS Section 13)

CREATE TABLE ledger (
    ledger_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ledger_name VARCHAR(255) NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    opening_balance DECIMAL(15,2) NOT NULL,
    total_debit DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    total_credit DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    closing_balance DECIMAL(15,2) NULL,
    status ENUM('ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    previous_ledger_id BIGINT NULL,
    next_ledger_id BIGINT NULL,
    active_flag TINYINT GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
    CONSTRAINT fk_ledger_previous
        FOREIGN KEY (previous_ledger_id) REFERENCES ledger (ledger_id),
    CONSTRAINT fk_ledger_next
        FOREIGN KEY (next_ledger_id) REFERENCES ledger (ledger_id),
    CONSTRAINT chk_ledger_opening_balance_nonneg CHECK (opening_balance >= 0),
    CONSTRAINT uq_ledger_one_active UNIQUE (active_flag)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE ledger_transaction (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ledger_id BIGINT NOT NULL,
    transaction_date DATE NOT NULL,
    transaction_type ENUM('DEBIT','CREDIT') NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_ledger
        FOREIGN KEY (ledger_id) REFERENCES ledger (ledger_id),
    CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transaction_reason_not_blank CHECK (TRIM(reason) <> '')
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX idx_ledger_transaction_ledger_date
    ON ledger_transaction (ledger_id, transaction_date);