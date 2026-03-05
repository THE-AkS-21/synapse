-- Database migration script for room-based messaging
-- Version: V2__add_room_support.sql

-- Add new columns to messages table
ALTER TABLE messages ADD COLUMN IF NOT EXISTS message_id VARCHAR(36);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS room_id VARCHAR(255);

-- Create unique index on message_id for deduplication
CREATE UNIQUE INDEX IF NOT EXISTS idx_message_id ON messages(message_id);

-- Create index on room_id and timestamp for efficient room message queries
CREATE INDEX IF NOT EXISTS idx_room_timestamp ON messages(room_id, timestamp DESC);

-- Create rooms table
CREATE TABLE IF NOT EXISTS rooms (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('PUBLIC', 'PRIVATE', 'DIRECT')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on rooms
CREATE INDEX IF NOT EXISTS idx_room_type ON rooms(type);
CREATE INDEX IF NOT EXISTS idx_room_name ON rooms(name);

-- Create room_participants junction table
CREATE TABLE IF NOT EXISTS room_participants (
    room_id VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id, user_id),
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create default "general" room for backward compatibility
INSERT INTO rooms (id, name, type, created_at)
VALUES ('general', 'general', 'PUBLIC', CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- Update existing messages to belong to the "general" room
UPDATE messages
SET room_id = 'general'
WHERE room_id IS NULL;

-- Generate UUIDs for existing messages (PostgreSQL specific)
UPDATE messages
SET message_id = gen_random_uuid()::text
WHERE message_id IS NULL;

-- Make message_id and room_id non-nullable after backfilling
ALTER TABLE messages ALTER COLUMN message_id SET NOT NULL;
-- Note: room_id can remain nullable for flexibility
