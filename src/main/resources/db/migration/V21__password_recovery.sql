CREATE TABLE password_recoveries (
 user_id BIGINT PRIMARY KEY REFERENCES school_users(id) ON DELETE CASCADE,
 code_hash VARCHAR(255),
 code_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 resend_available_at TIMESTAMP WITH TIME ZONE NOT NULL,
 code_attempts INTEGER NOT NULL DEFAULT 0,
 reset_token_hash VARCHAR(255),
 reset_expires_at TIMESTAMP WITH TIME ZONE
);
