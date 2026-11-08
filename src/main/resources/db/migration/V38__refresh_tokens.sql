CREATE TABLE refresh_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_refresh_tokens_session ON refresh_tokens(session_id);
