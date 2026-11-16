-- Existing databases: run once before starting the updated server.
ALTER TABLE conversation_members ADD COLUMN last_read_message_id INT NOT NULL DEFAULT 0;
ALTER TABLE messages ADD COLUMN client_message_id VARCHAR(36) NULL,
    ADD UNIQUE KEY uq_message_client (sender_id, client_message_id);
