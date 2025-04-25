-- Drop the trigger associated with the identity table
DROP TRIGGER IF EXISTS trigger_set_updated_at_timestamp_on_identity ON identity;

DROP TABLE identity;

DROP TYPE identity_strategy;

