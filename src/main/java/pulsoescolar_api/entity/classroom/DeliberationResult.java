package pulsoescolar_api.entity.classroom;

public enum DeliberationResult {
    APPROVED, FAILED;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static DeliberationResult fromJson(String value) { return valueOf(value); }
}
