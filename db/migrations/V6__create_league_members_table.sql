CREATE TABLE league_members (
    league_id BIGINT NOT NULL REFERENCES leagues(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (league_id, user_id)
);
