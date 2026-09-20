CREATE TABLE player_prices (
    player_id INT PRIMARY KEY,
    now_cost INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
