ALTER TABLE guardian_invitations ADD COLUMN renewal_session_id UUID;

CREATE INDEX idx_guardian_invitation_renewal_session
    ON guardian_invitations(confirmation_id, renewal_session_id);
