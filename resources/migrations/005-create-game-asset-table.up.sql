-- Create enum type for game_asset_status
CREATE TYPE game_asset_status_enum AS ENUM (
    'PENDING',
    'UPLOADED',
    'ERROR'
);

-- Create game_asset table
CREATE TABLE game_asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mime_type TEXT NOT NULL,
    img_url TEXT NOT NULL,
    status game_asset_status_enum NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT get_current_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT get_current_timestamp()
);

-- Trigger to call the function before updating the game_asset table
-- Assumes a generic set_updated_at_timestamp() function exists
CREATE TRIGGER trigger_set_updated_at_timestamp_on_game_asset
BEFORE UPDATE ON game_asset
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();
