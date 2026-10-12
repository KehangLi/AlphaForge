ALTER TABLE app_user
    ADD COLUMN user_type varchar(20) NOT NULL DEFAULT 'REGISTERED',
    ALTER COLUMN email DROP NOT NULL,
    ADD CONSTRAINT chk_app_user_type
        CHECK (user_type IN ('GUEST', 'REGISTERED'));
