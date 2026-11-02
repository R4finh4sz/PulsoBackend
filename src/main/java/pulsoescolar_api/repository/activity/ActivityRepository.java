package pulsoescolar_api.repository.activity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pulsoescolar_api.entity.activity.Activity;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findBySubjectIdOrderByCreatedAtDescIdDesc(Long subjectId);
    List<Activity> findByCreatedByIdOrderByCreatedAtDescIdDesc(Long createdById);
    List<Activity> findBySubjectClassroomIdOrderByCreatedAtDescIdDesc(Long classroomId);

    @Query("""
            select distinct a from Activity a join a.subject s left join s.collaborators collaborator
            join s.classroom.teachers teacher
            where teacher.id = :teacherId and (s.teacher.id = :teacherId or collaborator.id = :teacherId)
            and s.classroom.deletedAt is null
            and (s.classroom.school is null or s.classroom.school member of teacher.schools)
            order by a.createdAt desc, a.id desc
            """)
    List<Activity> findForTeacher(Long teacherId);
}
