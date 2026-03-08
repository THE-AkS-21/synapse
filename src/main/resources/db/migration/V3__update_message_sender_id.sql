-- 1. Add the new column (nullable initially so we can backfill)
ALTER TABLE messages ADD COLUMN IF NOT EXISTS sender_id BIGINT;

-- 2. Backfill the sender_id by matching the old username to the users table
UPDATE messages m
SET sender_id = u.id
    FROM users u
WHERE m.sender_username = u.username;

-- 3. Delete any orphaned messages where the user no longer exists
DELETE FROM messages WHERE sender_id IS NULL;

-- 4. Now that all rows have a value, enforce the NOT NULL constraint
ALTER TABLE messages ALTER COLUMN sender_id SET NOT NULL;

-- 5. Drop the old column
ALTER TABLE messages DROP COLUMN IF EXISTS sender_username;

-- 6. Update the index to use the new sender_id column
DROP INDEX IF EXISTS idx_sender_timestamp;
CREATE INDEX IF NOT EXISTS idx_sender_timestamp ON messages(sender_id, timestamp DESC);