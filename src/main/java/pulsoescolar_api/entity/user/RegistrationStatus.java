package pulsoescolar_api.entity.user;

public enum RegistrationStatus {
    PENDING, APPROVED, REJECTED;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static RegistrationStatus fromJson(String value) { return valueOf(value); }
}
