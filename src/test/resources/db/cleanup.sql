-- Clear committed HTTP writes and JDBC sessions before every test.
-- Keep the schema and flyway_schema_history so migrations run only at startup.
TRUNCATE TABLE
    spring_session_attributes,
    spring_session,
    account_activity,
    import_batch,
    brokerage_account,
    app_user;
