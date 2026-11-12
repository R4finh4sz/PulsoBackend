package pulsoescolar_api.mapper.subject;

import org.junit.jupiter.api.Test;
import pulsoescolar_api.entity.classroom.Classroom;
import pulsoescolar_api.entity.subject.Subject;
import pulsoescolar_api.entity.subject.SubjectTopic;
import pulsoescolar_api.entity.user.SchoolUser;
import static org.assertj.core.api.Assertions.assertThat;

class SubjectMapperTest {
    private Subject subject() {
        var subject = new Subject();
        var classroom = new Classroom();
        classroom.setId(1L);
        var teacher = new SchoolUser();
        teacher.setId(2L);
        subject.setClassroom(classroom);
        subject.setTeacher(teacher);
        return subject;
    }

    @Test
    void ementa_comTopicos_deveConcatenarNaOrdemSemTruncar() {
        var subject = subject();
        subject.setSyllabus("Texto geral anterior");
        subject.getTopics().add(new SubjectTopic("Tema A", "A".repeat(2000), ""));
        subject.getTopics().add(new SubjectTopic("Tema B", "B".repeat(2000), ""));
        subject.getTopics().add(new SubjectTopic("Tema C", "C".repeat(2000), ""));

        var response = new SubjectMapper().toResponse(subject);

        assertThat(response.syllabus()).isEqualTo("A".repeat(2000) + "\n\n" + "B".repeat(2000) + "\n\n" + "C".repeat(2000));
        assertThat(response.topics()).hasSize(3);
    }

    @Test
    void ementa_semTopicos_devePreservarTextoAnterior() {
        var subject = subject();
        subject.setSyllabus("Ementa anterior");

        var response = new SubjectMapper().toResponse(subject);

        assertThat(response.syllabus()).isEqualTo("Ementa anterior");
        assertThat(response.topics()).isEmpty();
    }
}
