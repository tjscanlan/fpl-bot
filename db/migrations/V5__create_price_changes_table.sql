CREATE TABLE price_changes (
    id BIGSERIAL PRIMARY KEY,
    player_id INT NOT NULL,
    old_cost INT NOT NULL,
    new_cost INT NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
