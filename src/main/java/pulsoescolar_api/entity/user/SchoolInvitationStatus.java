package pulsoescolar_api.entity.user;

public enum SchoolInvitationStatus {
    PENDING, ACCEPTED, REJECTED;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static SchoolInvitationStatus fromJson(String value) { return valueOf(value); }
}
