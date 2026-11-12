-- Historical events have no recoverable actor. Keep their actor_user_id NULL.
-- No cascading foreign key: audit history must survive removal of an account.
ALTER TABLE audit_events ADD COLUMN actor_user_id BIGINT;

CREATE INDEX idx_audit_events_actor_user_id ON audit_events (actor_user_id);

CREATE OR REPLACE VIEW audit_events_grafana AS
SELECT occurred_at,
       CASE event_type
           WHEN 1 THEN 'TERMS_ACCEPTED'
           WHEN 2 THEN 'ACCOUNT_DELETED'
           WHEN 3 THEN 'SCHOOL_CREATED'
           WHEN 4 THEN 'COORDINATOR_CREATED'
       END AS event_name,
       actor_user_id
FROM audit_events;
