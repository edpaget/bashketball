
CREATE TABLE session (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE
);

-- Index for potential lookups by actor_id (corrected column name)
CREATE INDEX idx_session_actor_id ON session(actor_id);

-- Add foreign key constraint from session.actor_id to actor.id
ALTER TABLE session
ADD CONSTRAINT fk_session_actor_id
FOREIGN KEY (actor_id) REFERENCES actor(id);
