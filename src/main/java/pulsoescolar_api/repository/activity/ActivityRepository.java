package pulsoescolar_api.repository.activity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pulsoescolar_api.entity.activity.Activity;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activity a where a.closingReminderSent = false "
            + "and a.closesAt > :now and a.closesAt < :endOfDay order by a.id")
    List<Activity> findForClosingReminder(java.time.Instant now, java.time.Instant endOfDay);

    interface Recipient {
        Long getActivityId();
        Long getStudentId();
    }

    @Query("select a.id as activityId, r.id as studentId from Activity a join a.recipients r "
            + "where a.id in :activityIds and r.deletedAt is null")
    List<Recipient> findRecipients(java.util.Collection<Long> activityIds);
    List<Activity> findBySubjectIdOrderByCreatedAtDescIdDesc(Long subjectId);
    List<Activity> findByCreatedByIdOrderByCreatedAtDescIdDesc(Long createdById);
    List<Activity> findBySubjectClassroomIdOrderByCreatedAtDescIdDesc(Long classroomId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"subject", "subject.classroom", "subject.classroom.school"})
    @Query("""
            select distinct a from Activity a join a.subject s left join s.collaborators collaborator
            join s.classroom.teachers teacher
            where teacher.id = :teacherId and (s.teacher.id = :teacherId or collaborator.id = :teacherId)
            and s.classroom.deletedAt is null
            and (s.classroom.school is null or s.classroom.school.active = true)
            and (s.classroom.school is null or s.classroom.school member of teacher.schools)
            order by a.createdAt desc, a.id desc
            """)
    List<Activity> findForTeacher(Long teacherId);
}
