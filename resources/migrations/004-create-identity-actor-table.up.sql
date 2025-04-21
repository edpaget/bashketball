
-- Create the identity_actor join table
CREATE TABLE identity_actor (
    provider identity_strategy NOT NULL,
    provider_identity TEXT NOT NULL,
    actor_id UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,

    -- Define the composite primary key
    PRIMARY KEY (provider, provider_identity, actor_id),

    -- Define the foreign key to the identity table
    CONSTRAINT fk_identity_actor_identity
        FOREIGN KEY (provider, provider_identity)
        REFERENCES identity(provider, provider_identity)
        ON DELETE CASCADE, -- If an identity is deleted, remove the link

    -- Define the foreign key to the actor table
    CONSTRAINT fk_identity_actor_actor
        FOREIGN KEY (actor_id)
        REFERENCES actor(id)
        ON DELETE CASCADE -- If an actor is deleted, remove the link
);

CREATE INDEX idx_identity_actor_actor_id ON identity_actor(actor_id);
CREATE INDEX idx_identity_actor_identity ON identity_actor(provider, provider_identity);
