-- Create the enum type for card_type
CREATE TYPE card_type_enum AS ENUM (
    'PLAYER_CARD',
    'ABILITY_CARD',
    'SPLIT_PLAY_CARD',
    'PLAY_CARD',
    'COACHING_CARD',
    'STANDARD_ACTION_CARD',
    'TEAM_ASSET_CARD',
    'INVALID'
);

-- Create the enum type for size
CREATE TYPE size_enum AS ENUM (
    'INVALID',
    'SM',
    'MD',
    'LG'
);

-- Create the game_cards table
CREATE TABLE game_card (
    name TEXT NOT NULL,
    version TEXT NOT NULL,
    img_url TEXT,
    card_type card_type_enum NOT NULL,
    deck_size INT,
    sht INT,
    pss INT,
    def INT,
    speed INT,
    size size_enum,
    abilities JSONB,
    offense TEXT,
    defense TEXT,
    play TEXT,
    coaching TEXT,
    fate INT,
    asset_power TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT get_current_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT get_current_timestamp(),
    PRIMARY KEY (name, version)
);

-- Trigger to call the function before updating the game_card table
CREATE TRIGGER trigger_set_updated_at_timestamp_on_game_card
BEFORE UPDATE ON game_card
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();
