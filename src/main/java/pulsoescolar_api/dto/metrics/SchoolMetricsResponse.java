package pulsoescolar_api.dto.metrics;

public record SchoolMetricsResponse(Long schoolId, String schoolName, BigNumbers bigNumbers) {
    public record BigNumbers(long students, long teachers, long classrooms, long subjects,
            long activities, long submissions) {}
}
