CREATE DATABASE IF NOT EXISTS autotrader
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE autotrader;

CREATE TABLE IF NOT EXISTS trade_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    run_at DATETIME(6) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    window_size INT NOT NULL,
    close_prices_json LONGTEXT NOT NULL,
    last_close INT NULL,
    now_price INT NULL,
    now_price_source VARCHAR(80) NULL,
    market_closed BOOLEAN NULL,
    trade_signal VARCHAR(20) NULL,
    z_score DOUBLE NULL,
    rolling_mean DOUBLE NULL,
    rolling_stddev DOUBLE NULL,
    reason VARCHAR(500) NULL,
    data_quality_status VARCHAR(20) NULL,
    data_quality_reasons_json LONGTEXT NULL,
    price_change_rate DOUBLE NULL,
    budget_krw INT NULL,
    order_side VARCHAR(20) NULL,
    order_qty INT NULL,
    position_qty_before INT NULL,
    position_qty_after_estimate INT NULL,
    order_attempted BOOLEAN NULL,
    order_executed BOOLEAN NULL,
    order_skip_reason VARCHAR(800) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trade_runs_run_id (run_id),
    KEY idx_trade_runs_symbol_run_at (symbol, run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_audits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(20) NULL,
    quantity INT NULL,
    attempted BOOLEAN NULL,
    executed BOOLEAN NULL,
    rt_cd VARCHAR(30) NULL,
    msg_cd VARCHAR(80) NULL,
    msg1 VARCHAR(500) NULL,
    ord_no VARCHAR(80) NULL,
    krx_fwdg_ord_orgno VARCHAR(80) NULL,
    raw_response LONGTEXT NULL,
    PRIMARY KEY (id),
    KEY idx_order_audits_run_id (run_id),
    KEY idx_order_audits_symbol_created_at (symbol, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS data_quality_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reasons_json LONGTEXT NULL,
    price_change_rate DOUBLE NULL,
    PRIMARY KEY (id),
    KEY idx_data_quality_logs_run_id (run_id),
    KEY idx_data_quality_logs_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS positions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    available_quantity INT NULL,
    avg_price INT NULL,
    source VARCHAR(40) NULL,
    last_synced_at DATETIME(6) NULL,
    last_sync_message VARCHAR(800) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_positions_symbol (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

