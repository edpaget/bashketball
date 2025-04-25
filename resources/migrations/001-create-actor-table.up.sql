CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Function to automatically set updated_at to the current timestamp
CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE actor (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username TEXT UNIQUE NOT NULL,
    enrollment_state TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), -- Changed type and default
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()  -- Changed type and default
);

-- Trigger to call the function before updating the actor table
CREATE TRIGGER trigger_set_updated_at_timestamp_on_actor
BEFORE UPDATE ON actor
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();
