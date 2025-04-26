-- Drop the trigger associated with the actor table
DROP TRIGGER IF EXISTS trigger_set_updated_at_timestamp_on_actor ON actor;
-- Drop the function (only needs to be dropped once, here in the first migration's down script)
DROP FUNCTION IF EXISTS set_updated_at_timestamp();
DROP TABLE actor;
DROP FUNCTION IF EXISTS get_current_timestamp();
