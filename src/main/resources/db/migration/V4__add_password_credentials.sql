ALTER TABLE app_user
    ADD COLUMN password_hash varchar(255),
    ADD CONSTRAINT chk_app_user_guest_password
        CHECK (user_type <> 'GUEST' OR password_hash IS NULL);

CREATE UNIQUE INDEX idx_app_user_email_normalized
    ON app_user (lower(btrim(email)));
