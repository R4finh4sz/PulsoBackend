package pulsoescolar_api.integration.activity;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pulsoescolar_api.dto.activity.*;
import pulsoescolar_api.dto.classroom.ClassroomResponse;
import pulsoescolar_api.dto.subject.SubjectResponse;
import pulsoescolar_api.dto.user.UserExportResponse;
import pulsoescolar_api.entity.user.*;
import pulsoescolar_api.integration.support.HttpIntegrationSupport;
import pulsoescolar_api.repository.activity.*;
import pulsoescolar_api.repository.user.UserRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;

class ActivityIntegrationTest extends HttpIntegrationSupport {
    @Autowired UserRepository users;
    @Autowired ActivityRepository activities;
    @Autowired QuizSubmissionRepository submissions;
    @Autowired PlatformTransactionManager transactions;
    @Autowired pulsoescolar_api.repository.school.SchoolRepository schools;
    ClassroomResponse room;
    SubjectResponse subject;
    SchoolUser student;
    String studentToken;

    TransactionTemplate tx() { return new TransactionTemplate(transactions); }

    @BeforeEach
    void setupQuiz() throws Exception {
        room = vincularProfessor(criarSala());
        subject = request(POST, "/api/classrooms/" + room.id() + "/subjects", teacherToken,
                Map.of("name", "Matemática", "syllabus", "", "topics", List.of(
                        Map.of("title", "Números", "description", "Operações", "references", ""))), SubjectResponse.class).getBody();
        student = criarUsuario(Role.STUDENT);
        request(PUT, "/api/classrooms/" + room.id() + "/students/" + student.getId(), managerToken, null, String.class);
        studentToken = autenticar(student);
        tx().executeWithoutResult(status -> subjects.findById(subject.id()).orElseThrow().getThemes().add("Números"));
    }

    String activityPath() { return "/api/subjects/" + subject.id() + "/activities"; }

    @Test
    void rankingCalculatesAveragesTiesFiltersAndCalendarBoundaries() {
        var activity = create();
        var extra = create();
        var school = school();
        var otherSchool = school();
        var second = criarUsuario(Role.STUDENT);
        var third = criarUsuario(Role.STUDENT);
        tx().executeWithoutResult(status -> {
            classrooms.findById(room.id()).orElseThrow().setSchool(school);
            for (var user : List.of(student, second, third)) {
                var submission = new pulsoescolar_api.entity.activity.QuizSubmission();
                submission.setStudent(users.findById(user.getId()).orElseThrow());
                submission.setActivity(activities.findById(activity.id()).orElseThrow());
                submission.setSubmittedAt(java.time.Instant.parse("2026-02-01T03:00:00Z"));
                if (!user.getId().equals(third.getId())) {
                    submission.setScore(new BigDecimal("80.00"));
                    submission.setCorrectAnswers(8);
                    submission.setGradedAt(java.time.Instant.parse("2026-05-01T03:00:00Z"));
                }
                submissions.save(submission);
            }
        });
        String path = "/api/rankings/students?year=2026&bimester=1";
        var ranked = request(GET, path + "&schoolId=" + school.getId(), studentToken, null,
                StudentRankingResponse[].class);
        assertThat(ranked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ranked.getBody()).hasSize(2).allSatisfy(row -> {
            assertThat(row.position()).isEqualTo(1);
            assertThat(row.totalActivities()).isEqualTo(1);
            assertThat(row.totalScore()).isEqualByComparingTo("80");
            assertThat(row.averageScore()).isEqualByComparingTo("80");
        });
        assertThat(request(GET, path, teacherToken, null, StudentRankingResponse[].class).getBody())
                .extracting(StudentRankingResponse::studentId).contains(student.getId(), second.getId())
                .doesNotContain(third.getId());
        assertThat(request(GET, path + "&schoolId=" + otherSchool.getId(), teacherToken, null,
                StudentRankingResponse[].class).getBody()).isEmpty();
        tx().executeWithoutResult(status -> {
            var submission = new pulsoescolar_api.entity.activity.QuizSubmission();
            submission.setStudent(users.findById(student.getId()).orElseThrow());
            submission.setActivity(activities.findById(extra.id()).orElseThrow());
            submission.setSubmittedAt(java.time.Instant.parse("2026-02-01T03:00:00Z"));
            submission.setGradedAt(java.time.Instant.parse("2026-02-03T03:00:00Z"));
            submission.setScore(new BigDecimal("100.00"));
            submission.setCorrectAnswers(10);
            submissions.save(submission);
        });
        var updated = request(GET, path + "&schoolId=" + school.getId(), teacherToken, null,
                StudentRankingResponse[].class).getBody();
        assertThat(updated[0].studentId()).isEqualTo(student.getId());
        assertThat(updated[0].totalActivities()).isEqualTo(2);
        assertThat(updated[0].totalScore()).isEqualByComparingTo("180");
        assertThat(updated[0].averageScore()).isEqualByComparingTo("90");
        assertThat(updated[1].position()).isEqualTo(2);
        String[] starts = {"2026-02-01", "2026-04-01", "2026-07-01", "2026-10-01"};
        String[] ends = {"2026-04-30", "2026-07-30", "2026-10-30", "2026-12-30"};
        for (int i = 0; i < 4; i++) {
            var start = java.time.LocalDate.parse(starts[i]).atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toInstant();
            var end = java.time.LocalDate.parse(ends[i]).plusDays(1)
                    .atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toInstant();
            for (var instant : List.of(start.minusSeconds(1), start, end.minusSeconds(1), end)) {
                tx().executeWithoutResult(status -> submissions.findByActivityIdOrderBySubmittedAtDescIdDesc(activity.id())
                        .forEach(s -> s.setSubmittedAt(instant)));
                var rows = request(GET, "/api/rankings/students?year=2026&bimester=" + (i + 1)
                        + "&schoolId=" + school.getId(), teacherToken, null, StudentRankingResponse[].class).getBody();
                assertThat(Arrays.stream(rows).anyMatch(row -> row.studentId().equals(second.getId())))
                        .isEqualTo(!instant.isBefore(start) && instant.isBefore(end));
            }
        }
        assertThat(request(GET, path, null, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        for (String invalid : List.of("year=2025&bimester=1", "year=2026&bimester=0", "year=2026&bimester=5")) {
            assertThat(request(GET, "/api/rankings/students?" + invalid, teacherToken, null, String.class)
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(request(GET, path + "&schoolId=9223372036854775807", teacherToken, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    Map<String, Object> input() {
        return Map.of("questions", Collections.nCopies(10, Map.of("question", " 1 + 1? ",
                "options", List.of("1", "2", "3", "4"), "theme", " Números ", "correctOption", 1)));
    }

    List<Integer> correctAnswers() { return Collections.nCopies(10, 1); }
    List<Integer> wrongAnswers() { return Collections.nCopies(10, 0); }
    List<Integer> partialAnswers() { return List.of(1, 1, 1, 1, 1, 1, 1, 0, 0, 0); }

    ActivityResponse create() {
        var response = request(POST, activityPath(), teacherToken, input(), ActivityResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().closesAt()).isNull();
        assertThat(response.getBody().status()).isEqualTo(ActivityStatus.OPEN);
        assertThat(response.getBody().questionCount()).isEqualTo(10);
        return response.getBody();
    }

    SubmissionResponse submit(ActivityResponse activity, List<Integer> answers) {
        var response = request(POST, "/api/activities/" + activity.id() + "/submissions", studentToken,
                Map.of("answers", answers, "studentId", teacher.getId(), "score", 100), SubmissionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().studentId()).isEqualTo(student.getId());
        assertThat(response.getBody().score()).isNull();
        assertThat(response.getBody().themePerformance()).isEmpty();
        return response.getBody();
    }

    @Test
    void completeFlowPersistsSubmissionGradesAndListsTeacherAndStudentResults() {
        var activity = create();
        assertThat(request(GET, activityPath(), studentToken, null, ActivityResponse[].class).getBody()).contains(activity);
        var quiz = request(GET, "/api/activities/" + activity.id() + "/quiz", studentToken, null, QuizResponse.class);
        assertThat(quiz.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(quiz.getBody().questions()).hasSize(10);
        assertThat(quiz.getBody().questions().getFirst().question()).isEqualTo("1 + 1?");
        var submission = submit(activity, partialAnswers());
        assertThat(submission.gradedAt()).isNull();
        var grade = request(PATCH, "/api/submissions/" + submission.id() + "/grade", teacherToken, null, SubmissionResponse.class);
        assertThat(grade.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(grade.getBody().score()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(grade.getBody().correctAnswers()).isEqualTo(7);
        assertThat(grade.getBody().themePerformance()).containsExactly(
                new ThemePerformanceResponse("Números", 10, 7, 3, new BigDecimal("70.00"), true));
        assertThat(grade.getBody().gradedAt()).isNotNull();
        assertThat(request(GET, "/api/submissions/" + submission.id() + "/result", studentToken, null,
                SubmissionResponse.class).getBody()).isEqualTo(grade.getBody());
        var mine = request(GET, "/api/student/activities", studentToken, null, StudentActivityResponse[].class);
        assertThat(mine.getBody()).hasSize(1);
        assertThat(mine.getBody()[0].submission()).isEqualTo(grade.getBody());
        var teacherActivities = request(GET, "/api/teacher/activities", teacherToken, null, TeacherActivityResponse[].class);
        assertThat(teacherActivities.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(teacherActivities.getBody()).hasSize(1);
        assertThat(teacherActivities.getBody()[0].submissions()).containsExactly(grade.getBody());
        assertThat(submissions.findById(submission.id()).orElseThrow().getScore()).isEqualByComparingTo("70.00");
        assertThat(request(PATCH, "/api/submissions/" + submission.id() + "/grade", teacherToken, null,
                String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void answerKeyIsNeverSerializedAndExportContainsOnlyOwnSubmissions() throws Exception {
        var activity = create();
        var submission = submit(activity, correctAnswers());
        var outsider = criarUsuario(Role.STUDENT);
        var outsiderToken = autenticar(outsider);
        var export = request(GET, "/api/me/export", studentToken, null, UserExportResponse.class).getBody();
        assertThat(export.submissions()).containsExactly(submission);
        assertThat(export.authoredActivities()).isEmpty();
        assertThat(request(GET, "/api/me/export", outsiderToken, null, UserExportResponse.class).getBody().submissions()).isEmpty();
        assertThat(request(GET, "/api/me/export", teacherToken, null, UserExportResponse.class).getBody().authoredActivities())
                .extracting(q -> q.activity().id()).containsExactly(activity.id());
        for (String path : List.of("/api/activities/" + activity.id() + "/quiz", activityPath(),
                "/api/student/activities", "/api/me/export")) {
            assertThat(request(GET, path, studentToken, null, String.class).getBody())
                    .doesNotContain("correctOption", "passwordHash", "gradedBy", teacher.getEmail());
        }
    }

    @Test
    void unrelatedStudentsAndClassroomTeachersCannotAccessSubjectActivitiesOrResults() throws Exception {
        var activity = create();
        var submission = submit(activity, wrongAnswers());
        var outsiderStudent = criarUsuario(Role.STUDENT);
        var outsiderStudentToken = autenticar(outsiderStudent);
        var outsiderTeacher = criarUsuario(Role.TEACHER);
        request(PUT, "/api/classrooms/" + room.id() + "/teachers/" + outsiderTeacher.getId(), managerToken, null, String.class);
        var outsiderTeacherToken = autenticar(outsiderTeacher);
        for (String token : List.of(outsiderStudentToken, outsiderTeacherToken, managerToken)) {
            assertThat(request(GET, "/api/activities/" + activity.id() + "/quiz", token, null, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(request(GET, "/api/submissions/" + submission.id() + "/result", token, null, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(request(POST, activityPath(), token, input(), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(request(PATCH, "/api/submissions/" + submission.id() + "/grade", studentToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(GET, "/api/student/activities", teacherToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(GET, "/api/teacher/activities", studentToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(GET, "/api/teacher/activities", outsiderTeacherToken, null, TeacherActivityResponse[].class).getBody()).isEmpty();
    }

    @Test
    void studentCannotSubmitMissingOrInvalidOptionsAndCannotChooseIdentityOrScore() {
        var activity = create();
        String path = "/api/activities/" + activity.id() + "/submissions";
        for (Object answers : List.of(List.of(), List.of(1), Collections.nCopies(10, -1),
                Collections.nCopies(10, 4), Arrays.asList(1, null, 0))) {
            assertThat(request(POST, path, studentToken, Map.of("answers", answers), String.class).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(request(POST, path, studentToken, Map.of(), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(POST, path, teacherToken, Map.of("answers", correctAnswers()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        submit(activity, correctAnswers());
        assertThat(request(POST, path, studentToken, Map.of("answers", wrongAnswers()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createValidatesQuizAndAnswerKeyBeforePersisting() {
        long count = activities.count();
        var invalid = new ArrayList<Map<String, Object>>();
        invalid.add(Map.of("questions", List.of()));
        invalid.add(Map.of("questions", Arrays.asList((Object) null)));
        var valid = Map.<String, Object>of("question", "Questão", "options", List.of("A", "B", "C", "D"),
                "theme", "Números", "correctOption", 0);
        invalid.add(Map.of("questions", Collections.nCopies(9, valid)));
        invalid.add(Map.of("questions", Collections.nCopies(11, valid)));
        for (var change : List.of(Map.of("correctOption", 4), Map.of("correctOption", -1),
                Map.of("correctOption", 1.5), Map.of("correctOption", "1"), Map.of("options", List.of(" A ", "a", "C", "D")),
                Map.of("options", List.of("A", "B", "C")), Map.of("options", List.of("A", "B", "C", "D", "E")),
                Map.of("question", "x".repeat(1001)), Map.of("question", " "), Map.of("theme", " "),
                Map.of("theme", "Tema inexistente"), Map.of("options", List.of("A", "B", "C", "x".repeat(501))))) {
            var question = new HashMap<String, Object>(valid); question.putAll(change);
            invalid.add(Map.of("questions", Collections.nCopies(10, question)));
        }
        for (var body : invalid) {
            assertThat(request(POST, activityPath(), teacherToken, body, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(activities.count()).isEqualTo(count);
    }

    @Test
    void simultaneousSubmissionsAndGradingAreAppliedOnce() {
        var activity = create();
        String path = "/api/activities/" + activity.id() + "/submissions";
        var first = CompletableFuture.supplyAsync(() -> request(POST, path, studentToken,
                Map.of("answers", correctAnswers()), String.class));
        var second = CompletableFuture.supplyAsync(() -> request(POST, path, studentToken,
                Map.of("answers", wrongAnswers()), String.class));
        assertThat(List.of(first.join().getStatusCode().value(), second.join().getStatusCode().value()))
                .containsExactlyInAnyOrder(201, 409);
        var submission = submissions.findByActivityIdAndStudentId(activity.id(), student.getId()).orElseThrow();
        String gradePath = "/api/submissions/" + submission.getId() + "/grade";
        var grade1 = CompletableFuture.supplyAsync(() -> request(PATCH, gradePath, teacherToken, null, String.class));
        var grade2 = CompletableFuture.supplyAsync(() -> request(PATCH, gradePath, teacherToken, null, String.class));
        assertThat(List.of(grade1.join().getStatusCode().value(), grade2.join().getStatusCode().value()))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(submissions.findByActivityIdOrderBySubmittedAtDescIdDesc(activity.id())).hasSize(1);
    }

    @Test
    void transferredStudentRetainsOwnResultAndDeletedClassroomPreservesHistory() {
        var activity = create(); var submission = submit(activity, correctAnswers());
        request(PATCH, "/api/submissions/" + submission.id() + "/grade", teacherToken, null, String.class);
        var otherRoom = criarSala();
        request(PUT, "/api/classrooms/" + otherRoom.id() + "/students/" + student.getId(), managerToken, null, String.class);
        assertThat(request(GET, "/api/activities/" + activity.id() + "/quiz", studentToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(GET, "/api/submissions/" + submission.id() + "/result", studentToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        request(DELETE, "/api/classrooms/" + room.id(), managerToken, null, String.class);
        assertThat(request(GET, "/api/activities/" + activity.id() + "/quiz", teacherToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(GET, "/api/teacher/activities", teacherToken, null, TeacherActivityResponse[].class).getBody()).isEmpty();
        assertThat(request(GET, "/api/me/export", studentToken, null, UserExportResponse.class).getBody().submissions()).hasSize(1);
        assertThat(activities.findById(activity.id())).isPresent();
        assertThat(submissions.findById(submission.id())).isPresent();
    }

    @Test
    void collaboratorCanGradeAndRemovalRevokesAccess() throws Exception {
        var collaborator = criarUsuario(Role.TEACHER);
        request(PUT, "/api/classrooms/" + room.id() + "/teachers/" + collaborator.getId(), managerToken, null, String.class);
        tx().executeWithoutResult(status -> subjects.findById(subject.id()).orElseThrow().getCollaborators()
                .add(users.findById(collaborator.getId()).orElseThrow()));
        var token = autenticar(collaborator);
        var activity = create(); var submission = submit(activity, correctAnswers());
        assertThat(request(GET, "/api/teacher/activities", token, null, TeacherActivityResponse[].class).getBody()).hasSize(1);
        assertThat(request(PATCH, "/api/submissions/" + submission.id() + "/grade", token, null,
                SubmissionResponse.class).getBody().score()).isEqualByComparingTo("100.00");
        tx().executeWithoutResult(status -> subjects.findById(subject.id()).orElseThrow().getCollaborators().clear());
        assertThat(request(GET, "/api/submissions/" + submission.id() + "/result", token, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void activeSchoolScopesQuizAndGradingWhileTeacherOverviewIncludesAllLinkedSchools() {
        var activity = create(); var submission = submit(activity, correctAnswers());
        var first = school(); var second = school();
        tx().executeWithoutResult(status -> {
            classrooms.findById(room.id()).orElseThrow().setSchool(first);
            for (var account : List.of(teacher, student)) {
                var managed = users.findById(account.getId()).orElseThrow();
                managed.setSchool(first); managed.getSchools().add(second);
            }
        });
        assertThat(request(GET, "/api/teacher/activities", teacherToken, null, TeacherActivityResponse[].class).getBody()).hasSize(1);
        for (String token : List.of(teacherToken, studentToken)) {
            request(PATCH, "/api/me/active-school", token, Map.of("schoolId", second.getId()), String.class);
            assertThat(request(GET, "/api/activities/" + activity.id() + "/quiz", token, null, String.class).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(request(GET, "/api/teacher/activities", teacherToken, null, TeacherActivityResponse[].class).getBody())
                .extracting(item -> item.activity().schoolId()).containsExactly(first.getId());
        assertThat(request(GET, "/api/student/activities", studentToken, null, StudentActivityResponse[].class).getBody()).isEmpty();
        assertThat(request(PATCH, "/api/submissions/" + submission.id() + "/grade", teacherToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(GET, "/api/submissions/" + submission.id() + "/result", studentToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    pulsoescolar_api.entity.school.School school() {
        var school = new pulsoescolar_api.entity.school.School();
        school.setNome("Escola quiz"); school.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        school.setLogradouro("Rua"); school.setBairro("Centro"); school.setCidade("Cidade");
        return schools.saveAndFlush(school);
    }

    @Test
    void gradeUsesStoredAnswerKeyAndReturnsZeroForAllWrongAnswers() {
        var activity = create(); var submission = submit(activity, wrongAnswers());
        var result = request(PATCH, "/api/submissions/" + submission.id() + "/grade", teacherToken,
                Map.of("score", 100, "correctAnswers", 3), SubmissionResponse.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().correctAnswers()).isZero();
        assertThat(result.getBody().score()).isEqualByComparingTo("0.00");
    }

    @Test
    void teacherOverviewIncludesMultipleSchoolsAndNestedRouteScopesClassroomAndSubject() {
        var firstActivity = create();
        var firstSchool = school(); var secondSchool = school();
        var secondRoom = vincularProfessor(criarSala());
        tx().executeWithoutResult(status -> {
            classrooms.findById(room.id()).orElseThrow().setSchool(firstSchool);
            classrooms.findById(secondRoom.id()).orElseThrow().setSchool(secondSchool);
            var managed = users.findById(teacher.getId()).orElseThrow();
            managed.setSchool(firstSchool); managed.getSchools().add(secondSchool);
            users.findById(student.getId()).orElseThrow().setSchool(firstSchool);
        });
        assertThat(request(PATCH, "/api/me/active-school", teacherToken,
                Map.of("schoolId", secondSchool.getId()), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var secondSubject = request(POST, "/api/classrooms/" + secondRoom.id() + "/subjects", teacherToken,
                Map.of("name", "Matemática", "syllabus", "", "topics", List.of(
                        Map.of("title", "Números", "description", "Operações", "references", ""))), SubjectResponse.class).getBody();
        tx().executeWithoutResult(status -> subjects.findById(secondSubject.id()).orElseThrow().getThemes().add("Números"));
        var secondActivity = request(POST, "/api/subjects/" + secondSubject.id() + "/activities", teacherToken,
                input(), ActivityResponse.class).getBody();
        var overview = request(GET, "/api/teacher/activities", teacherToken, null, TeacherActivityResponse[].class);
        assertThat(overview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(overview.getBody()).extracting(item -> item.activity().schoolId())
                .containsExactlyInAnyOrder(firstSchool.getId(), secondSchool.getId());
        String nested = "/api/classrooms/" + secondRoom.id() + "/subjects/" + secondSubject.id() + "/activities";
        var specific = request(GET, nested, teacherToken, null, ActivityResponse[].class);
        assertThat(specific.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(specific.getBody()).containsExactly(secondActivity);
        assertThat(request(GET, "/api/classrooms/" + room.id() + "/subjects/" + secondSubject.id() + "/activities",
                teacherToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        String firstNested = "/api/classrooms/" + room.id() + "/subjects/" + subject.id() + "/activities";
        assertThat(request(GET, firstNested, teacherToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(GET, firstNested, studentToken, null, ActivityResponse[].class).getBody())
                .extracting(ActivityResponse::id).containsExactly(firstActivity.id());
        assertThat(request(GET, nested, null, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        tx().executeWithoutResult(status -> users.findById(teacher.getId()).orElseThrow().getSchools()
                .removeIf(value -> value.getId().equals(firstSchool.getId())));
        assertThat(request(GET, "/api/teacher/activities", teacherToken, null, TeacherActivityResponse[].class).getBody())
                .extracting(item -> item.activity().id()).containsExactly(secondActivity.id());
    }

    @Test
    void deadlineIsOptionalAndClosedQuizzesRejectSubmissionsButAllowGrading() {
        var body = new HashMap<String, Object>(input());
        body.put("closesAt", "2020-01-01T00:00:00Z");
        var closed = request(POST, activityPath(), teacherToken, body, ActivityResponse.class);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(closed.getBody().closesAt()).isEqualTo(java.time.Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(closed.getBody().status()).isEqualTo(ActivityStatus.FINISHED);
        assertThat(request(POST, "/api/activities/" + closed.getBody().id() + "/submissions", studentToken,
                Map.of("answers", correctAnswers()), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request(GET, "/api/activities/" + closed.getBody().id() + "/quiz", studentToken, null,
                QuizResponse.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        body.put("closesAt", "2099-01-01T00:00:00Z");
        var open = request(POST, activityPath(), teacherToken, body, ActivityResponse.class).getBody();
        var submission = submit(open, correctAnswers());
        tx().executeWithoutResult(status -> activities.findById(open.id()).orElseThrow().setClosesAt(java.time.Instant.EPOCH));
        assertThat(request(PATCH, "/api/submissions/" + submission.id() + "/grade", teacherToken, null,
                SubmissionResponse.class).getBody().score()).isEqualByComparingTo("100.00");
        body.put("closesAt", "amanhã");
        assertThat(request(POST, activityPath(), teacherToken, body, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void acceptsFrontContractAtLengthLimitsAfterTrimming() {
        var question = Map.of("question", "  " + "x".repeat(1000) + "  ",
                "options", List.of("  " + "y".repeat(500) + "  ", "B", "C", "D"),
                "theme", "\uFEFF Números \u00A0", "correctOption", 3);
        var created = request(POST, activityPath(), teacherToken,
                Map.of("questions", Collections.nCopies(10, question)), ActivityResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var quiz = request(GET, "/api/activities/" + created.getBody().id() + "/quiz", studentToken, null, QuizResponse.class).getBody();
        assertThat(quiz.questions().getFirst().question()).hasSize(1000);
        assertThat(quiz.questions().getFirst().options().getFirst()).hasSize(500);
        assertThat(quiz.questions().getFirst().theme()).isEqualTo("Números");
        var submission = submit(created.getBody(), Collections.nCopies(10, 3));
        assertThat(request(PATCH, "/api/submissions/" + submission.id() + "/grade", teacherToken, null,
                SubmissionResponse.class).getBody().score()).isEqualByComparingTo("100.00");
    }

    @Test
    void allRoutesRequireAuthenticationAndMissingResourcesReturn404() {
        var activity = create();
        for (String path : List.of(activityPath(), "/api/student/activities", "/api/teacher/activities",
                "/api/activities/" + activity.id() + "/quiz", "/api/submissions/1/result")) {
            assertThat(request(GET, path, null, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(request(POST, activityPath(), null, input(), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(POST, "/api/activities/" + activity.id() + "/submissions", null,
                Map.of("answers", correctAnswers()), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(PATCH, "/api/submissions/1/grade", null, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        for (String path : List.of("/api/activities/9223372036854775807/quiz", "/api/submissions/9223372036854775807/result")) {
            assertThat(request(GET, path, teacherToken, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
