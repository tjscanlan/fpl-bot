CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    discord_user_id BIGINT NOT NULL UNIQUE,
    fpl_entry_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
