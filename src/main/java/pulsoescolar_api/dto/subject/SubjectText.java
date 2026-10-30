package pulsoescolar_api.dto.subject;

final class SubjectText {
    private SubjectText() {}

    static String trim(String value) {
        return value == null ? null : value.replaceAll("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$", "");
    }
}
