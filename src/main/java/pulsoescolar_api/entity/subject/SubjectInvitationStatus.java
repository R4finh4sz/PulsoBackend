package pulsoescolar_api.entity.subject;

public enum SubjectInvitationStatus {
    PENDING, ACCEPTED, REJECTED;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static SubjectInvitationStatus fromJson(String value) { return valueOf(value); }
}
