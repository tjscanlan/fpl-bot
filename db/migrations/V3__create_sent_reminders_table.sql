CREATE TABLE sent_reminders (
    id BIGSERIAL PRIMARY KEY,
    gameweek_id INT NOT NULL UNIQUE,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
