-- Do not silently discard historical duplicates when adding these constraints.
CREATE UNIQUE INDEX uq_import_batch_account_file_hash
    ON import_batch (brokerage_account_id, file_hash);

-- MVP policy: one nonblank broker transaction ID per account. Multi-row IDs
-- require a broker-specific fill identity before that export can be supported.
CREATE UNIQUE INDEX uq_account_activity_account_external_id
    ON account_activity (brokerage_account_id, btrim(external_transaction_id))
    WHERE nullif(btrim(external_transaction_id), '') IS NOT NULL;

CREATE SEQUENCE import_batch_sequence_seq;
ALTER TABLE import_batch ADD COLUMN import_sequence bigint;

-- Historical ties cannot reveal the original commit order; UUID breaks them
-- deterministically. All new imports receive their sequence under the user lock.
WITH ordered AS (
    SELECT id, row_number() OVER (ORDER BY created_at, id) AS sequence_number
    FROM import_batch
)
UPDATE import_batch batch
SET import_sequence = ordered.sequence_number
FROM ordered
WHERE batch.id = ordered.id;

SELECT setval('import_batch_sequence_seq', COALESCE(max(import_sequence), 0) + 1, false)
FROM import_batch;

ALTER TABLE import_batch
    ALTER COLUMN import_sequence SET DEFAULT nextval('import_batch_sequence_seq'),
    ALTER COLUMN import_sequence SET NOT NULL;
ALTER SEQUENCE import_batch_sequence_seq OWNED BY import_batch.import_sequence;

CREATE UNIQUE INDEX uq_import_batch_account_sequence
    ON import_batch (brokerage_account_id, import_sequence DESC);
