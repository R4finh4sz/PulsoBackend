package pulsoescolar_api.dto.user;

public enum RegistrationRequestType {
    REGISTRATION, TRANSFER;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static RegistrationRequestType fromJson(String value) { return valueOf(value); }
}
