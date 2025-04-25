-- Drop the trigger associated with the game_card table
DROP TRIGGER IF EXISTS trigger_set_updated_at_timestamp_on_game_card ON game_card;
-- Drop the game_cards table
DROP TABLE game_card;

-- Drop the enum type
DROP TYPE IF EXISTS size_enum;
DROP TYPE IF EXISTS card_type_enum;
