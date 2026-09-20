CREATE TABLE leagues (
    id BIGSERIAL PRIMARY KEY,
    discord_guild_id BIGINT NOT NULL,
    fpl_league_id BIGINT NOT NULL,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (discord_guild_id, fpl_league_id)
);
