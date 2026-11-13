package pulsoescolar_api.entity.classroom;

public enum DeliberationAction {
    TRANSFER, GRADUATE;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static DeliberationAction fromJson(String value) { return valueOf(value); }
}
