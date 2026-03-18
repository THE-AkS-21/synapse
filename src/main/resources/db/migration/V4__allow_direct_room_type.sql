-- V4__allow_direct_room_type.sql

-- Drop the existing constraint
ALTER TABLE rooms DROP CONSTRAINT IF EXISTS rooms_type_check;

-- Add the updated constraint including 'DIRECT'
ALTER TABLE rooms ADD CONSTRAINT rooms_type_check CHECK (type IN ('PUBLIC', 'PRIVATE', 'DIRECT'));