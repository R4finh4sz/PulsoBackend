package pulsoescolar_api.service.activity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.activity.Activity;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.entity.user.SchoolUser;
import pulsoescolar_api.repository.activity.ActivityRepository;
import pulsoescolar_api.repository.user.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class ActivityRecipientService {
    private final EntityManager entityManager;
    private final UserRepository users;
    private final ActivityRepository activities;
    private final Clock clock;

    public void capture(Activity activity) {
        var classroom = activity.getSubject().getClassroom();
        entityManager.lock(classroom, LockModeType.PESSIMISTIC_WRITE);
        users.findByClassroomIdAndRoleAndDeletedAtIsNull(classroom.getId(), Role.STUDENT).stream()
                .filter(student -> student.isInActiveSchool(classroom)).forEach(activity.getRecipients()::add);
        activity.setRecipientHistoryComplete(true);
    }

    public void assignOpenActivities(SchoolUser student) {
        var classroom = student.getActiveClassroom();
        if (classroom == null) return;
        // Matrícula e publicação usam a mesma trava para não perder destinatários simultâneos.
        entityManager.lock(classroom, LockModeType.PESSIMISTIC_WRITE);
        var now = clock.instant();
        activities.findBySubjectClassroomIdOrderByCreatedAtDescIdDesc(classroom.getId()).stream()
                .filter(activity -> activity.getClosesAt() == null || now.isBefore(activity.getClosesAt()))
                .forEach(activity -> activity.getRecipients().add(student));
    }
}
