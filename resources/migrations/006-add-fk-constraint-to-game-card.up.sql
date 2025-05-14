ALTER TABLE game_card
ADD CONSTRAINT fk_game_card_game_asset
FOREIGN KEY (game_asset_id) REFERENCES game_asset(id);
