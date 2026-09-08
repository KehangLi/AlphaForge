CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL,
    display_name varchar(120),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);

CREATE UNIQUE INDEX idx_app_user_email
    ON app_user (email);

CREATE TABLE brokerage_account (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    broker_name varchar(120) NOT NULL,
    account_name varchar(120) NOT NULL,
    account_number_masked varchar(80),
    base_currency varchar(3) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT fk_brokerage_account_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
);

CREATE INDEX idx_brokerage_account_user_id
    ON brokerage_account (user_id);

CREATE INDEX idx_brokerage_account_broker_name
    ON brokerage_account (broker_name);

CREATE TABLE import_batch (
    id uuid PRIMARY KEY,
    brokerage_account_id uuid NOT NULL,
    original_filename varchar(255) NOT NULL,
    file_hash varchar(64),
    status varchar(40) NOT NULL,
    total_rows integer NOT NULL,
    success_rows integer NOT NULL,
    failed_rows integer NOT NULL,
    started_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT fk_import_batch_brokerage_account
        FOREIGN KEY (brokerage_account_id)
        REFERENCES brokerage_account (id),
    CONSTRAINT chk_import_batch_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'))
);

CREATE INDEX idx_import_batch_brokerage_account_id
    ON import_batch (brokerage_account_id);

CREATE INDEX idx_import_batch_file_hash
    ON import_batch (file_hash);

CREATE INDEX idx_import_batch_status
    ON import_batch (status);

CREATE TABLE account_activity (
    id uuid PRIMARY KEY,
    import_batch_id uuid NOT NULL,
    brokerage_account_id uuid NOT NULL,
    row_number integer NOT NULL,
    external_transaction_id varchar(120),
    action varchar(120) NOT NULL,
    occurred_at timestamp(6) with time zone NOT NULL,
    isin varchar(12),
    ticker varchar(40),
    instrument_name varchar(255),
    notes text,
    number_of_shares numeric(28, 10),
    price_per_share numeric(28, 10),
    price_per_share_currency varchar(3),
    exchange_rate numeric(24, 10),
    result_amount numeric(19, 4),
    result_currency varchar(3),
    total_amount numeric(19, 4) NOT NULL,
    total_currency varchar(3) NOT NULL,
    withholding_tax numeric(19, 4),
    withholding_tax_currency varchar(3),
    currency_conversion_fee numeric(19, 4),
    currency_conversion_fee_currency varchar(3),
    merchant_name varchar(255),
    merchant_category varchar(120),
    raw_row_hash varchar(64),
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT fk_account_activity_import_batch
        FOREIGN KEY (import_batch_id)
        REFERENCES import_batch (id),
    CONSTRAINT fk_account_activity_brokerage_account
        FOREIGN KEY (brokerage_account_id)
        REFERENCES brokerage_account (id)
);

CREATE INDEX idx_account_activity_import_batch_id
    ON account_activity (import_batch_id);

CREATE INDEX idx_account_activity_brokerage_account_id
    ON account_activity (brokerage_account_id);

CREATE INDEX idx_account_activity_external_transaction_id
    ON account_activity (external_transaction_id);

CREATE INDEX idx_account_activity_occurred_at
    ON account_activity (occurred_at);

CREATE INDEX idx_account_activity_ticker
    ON account_activity (ticker);

CREATE INDEX idx_account_activity_action
    ON account_activity (action);
