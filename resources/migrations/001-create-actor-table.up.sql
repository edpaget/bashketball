CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Function to get the current timestamp, respecting a potential frozen_timestamp variable
CREATE OR REPLACE FUNCTION get_current_timestamp()
RETURNS TIMESTAMPTZ AS $$
DECLARE
    frozen_ts TEXT;
BEGIN
    -- Attempt to get the session variable. Handle potential NULL or empty string.
    frozen_ts := current_setting('vars.frozen_timestamp', true);

    IF frozen_ts IS NOT NULL AND frozen_ts <> '' THEN
        -- If the variable is set and not empty, try to cast it to TIMESTAMPTZ
        BEGIN
            RETURN frozen_ts::TIMESTAMPTZ;
        EXCEPTION WHEN others THEN
            -- If casting fails, fall back to NOW() or raise an error, depending on desired behavior.
            -- Here, we fall back to NOW().
            RETURN NOW();
        END;
    ELSE
        -- If the variable is not set or is empty, return the current time
        RETURN NOW();
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Function to automatically set updated_at to the current timestamp using get_current_timestamp()
CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = get_current_timestamp();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE actor (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username TEXT UNIQUE NOT NULL,
    enrollment_state TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT get_current_timestamp(), -- Use custom timestamp function
    updated_at TIMESTAMPTZ NOT NULL DEFAULT get_current_timestamp()  -- Use custom timestamp function
);

-- Trigger to call the function before updating the actor table
CREATE TRIGGER trigger_set_updated_at_timestamp_on_actor
BEFORE UPDATE ON actor
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();
