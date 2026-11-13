package pulsoescolar_api.entity.user;
public enum Role {
    ADMIN, TEACHER, PEDAGOGICAL_COORDINATOR, STUDENT;

    @com.fasterxml.jackson.annotation.JsonCreator
    public static Role fromJson(String value) { return valueOf(value); }
}
