-- =====================================================================
-- Sample data for the background cron job to process
-- =====================================================================
-- Run this against the transaction_processor database:
--   mysql -u root -p transaction_processor < sample_data.sql
--
-- Then start the app and watch the background job process these rows.
-- Or trigger manually: POST http://localhost:8080/api/transactions/process
-- =====================================================================

USE transaction_processor;

-- Clear any existing data (optional — comment out if you want to keep previous data)
DELETE FROM processing_log;
DELETE FROM account_balances;
DELETE FROM transaction_requests;

-- =====================================================================
-- SCENARIO 1: Normal account with CREDIT -> DEBIT -> CREDIT (in order)
-- =====================================================================

INSERT INTO transaction_requests (transaction_id, request_id, account_id, transaction_type, amount, currency, sequence_number, status, retry_count, max_retries, error_message, created_at)
VALUES
('TXN-0001', 'REQ-001', 'ACC-100', 'CREDIT',   1000.00, 'USD', 1, 'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0002', 'REQ-002', 'ACC-100', 'DEBIT',      200.00, 'USD', 2, 'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0003', 'REQ-003', 'ACC-100', 'CREDIT',      500.00, 'USD', 3, 'RECEIVED', 0, 3, NULL, NOW());

-- Expected after processing: balance = 1000 - 200 + 500 = 1300

-- =====================================================================
-- SCENARIO 2: Out-of-order arrival (seq 3 arrives before seq 1 and 2)
-- =====================================================================

INSERT INTO transaction_requests (transaction_id, request_id, account_id, transaction_type, amount, currency, sequence_number, status, retry_count, max_retries, error_message, created_at)
VALUES
('TXN-0004', 'REQ-004', 'ACC-200', 'CREDIT',    5000.00, 'USD', 3, 'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0005', 'REQ-005', 'ACC-200', 'CREDIT',    2000.00, 'USD', 1, 'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0006', 'REQ-006', 'ACC-200', 'DEBIT',      300.00, 'USD', 2, 'RECEIVED', 0, 3, NULL, NOW());

-- Expected after processing: processed in order 1->2->3, balance = 2000 - 300 + 5000 = 6700

-- =====================================================================
-- SCENARIO 3: Different account — independent of ACC-100 and ACC-200
-- =====================================================================

INSERT INTO transaction_requests (transaction_id, request_id, account_id, transaction_type, amount, currency, sequence_number, status, retry_count, max_retries, error_message, created_at)
VALUES
('TXN-0007', 'REQ-007', 'ACC-300', 'CREDIT',    10000.00, 'EUR', 1, 'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0008', 'REQ-008', 'ACC-300', 'DEBIT',     1500.00, 'EUR', 2, 'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0009', 'REQ-009', 'ACC-300', 'REVERSAL',   500.00, 'EUR', 3, 'RECEIVED', 0, 3, NULL, NOW());

-- Expected after processing: balance = 10000 - 1500 + 500 = 9000

-- =====================================================================
-- SCENARIO 4: Large batch — 10 transactions for a single account
-- =====================================================================

INSERT INTO transaction_requests (transaction_id, request_id, account_id, transaction_type, amount, currency, sequence_number, status, retry_count, max_retries, error_message, created_at)
VALUES
('TXN-0010', 'REQ-010', 'ACC-400', 'CREDIT',    500.00, 'USD', 1,  'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0011', 'REQ-011', 'ACC-400', 'CREDIT',    300.00, 'USD', 2,  'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0012', 'REQ-012', 'ACC-400', 'DEBIT',     100.00, 'USD', 3,  'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0013', 'REQ-013', 'ACC-400', 'CREDIT',    200.00, 'USD', 4,  'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0014', 'REQ-014', 'ACC-400', 'DEBIT',     400.00, 'USD', 5,  'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0015', 'REQ-015', 'ACC-400', 'CREDIT',    1000.00, 'USD', 6, 'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0016', 'REQ-016', 'ACC-400', 'DEBIT',     150.00, 'USD', 7,  'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0017', 'REQ-017', 'ACC-400', 'CREDIT',     50.00, 'USD', 8,  'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0018', 'REQ-018', 'ACC-400', 'DEBIT',      25.00, 'USD', 9,  'RECEIVED', 0, 3, NULL, NOW()),
('TXN-0019', 'REQ-019', 'ACC-400', 'CREDIT',    750.00, 'USD', 10, 'RECEIVED', 0, 3, NULL, NOW());

-- Expected after processing: 500+300-100+200-400+1000-150+50-25+750 = 2125

-- =====================================================================
-- QUERY TO CHECK RESULTS AFTER PROCESSING
-- =====================================================================

-- Check summary:
-- SELECT status, COUNT(*) AS count FROM transaction_requests GROUP BY status;

-- Check balances:
-- SELECT * FROM account_balances;

-- Check audit log for a specific transaction:
-- SELECT * FROM processing_log WHERE transaction_id = 'TXN-0001' ORDER BY logged_at;

-- Check all transactions:
-- SELECT transaction_id, request_id, account_id, transaction_type, amount, sequence_number, status FROM transaction_requests ORDER BY account_id, sequence_number;

