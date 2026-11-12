package pulsoescolar_api.service.audit;

public enum AuditEvent {
    TERMS_ACCEPTED(1), ACCOUNT_DELETED(2), SCHOOL_CREATED(3), COORDINATOR_CREATED(4);

    private final short code;

    AuditEvent(int code) { this.code = (short) code; }

    public short code() { return code; }
}
