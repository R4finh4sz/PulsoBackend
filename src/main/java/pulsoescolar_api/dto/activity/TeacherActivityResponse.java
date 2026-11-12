package pulsoescolar_api.dto.activity;

import java.util.List;

public record TeacherActivityResponse(ActivityResponse activity, List<SubmissionResponse> submissions) {}
