package pulsoescolar_api.entity.user;

public enum DeletionStatus {
    PENDING, APPROVED, REJECTED;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static DeletionStatus fromJson(String value) { return valueOf(value); }
}
