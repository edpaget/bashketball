
-- Create the ENUM type for identity strategies
CREATE TYPE identity_strategy AS ENUM ('INVALID', 'SIGN_IN_WITH_GOOGLE');

-- Create the identity table
CREATE TABLE identity (
    provider identity_strategy NOT NULL,
    provider_identity TEXT NOT NULL,
    email TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), -- Changed type and default
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), -- Changed type and default
    last_successful_at TIMESTAMPTZ,                -- Changed type
    last_failed_at TIMESTAMPTZ,                   -- Changed type
    PRIMARY KEY (provider, provider_identity)
);

-- Trigger to call the function before updating the identity table
CREATE TRIGGER trigger_set_updated_at_timestamp_on_identity
BEFORE UPDATE ON identity
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();
