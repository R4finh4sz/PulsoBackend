-- Append-only events. No user/school identifiers, free text, JSON or per-row ID.
CREATE TABLE audit_events (
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type SMALLINT NOT NULL CHECK (event_type IN (1, 2, 3, 4))
);

CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at);

-- Human-readable labels without repeating strings in every stored row.
CREATE VIEW audit_events_grafana AS
SELECT occurred_at,
       CASE event_type
           WHEN 1 THEN 'TERMS_ACCEPTED'
           WHEN 2 THEN 'ACCOUNT_DELETED'
           WHEN 3 THEN 'SCHOOL_CREATED'
           WHEN 4 THEN 'COORDINATOR_CREATED'
       END AS event_name
FROM audit_events;
