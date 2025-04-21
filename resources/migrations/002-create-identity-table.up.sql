
-- Create the ENUM type for identity strategies
CREATE TYPE identity_strategy AS ENUM ('INVALID', 'SIGN_IN_WITH_GOOGLE');

-- Create the identity table
CREATE TABLE identity (
    provider identity_strategy NOT NULL,
    provider_identity TEXT NOT NULL,
    email TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_successful_at TIMESTAMP WITHOUT TIME ZONE,
    last_failed_at TIMESTAMP WITHOUT TIME ZONE,
    PRIMARY KEY (provider, provider_identity)
);
