package pulsoescolar_api.service.activity;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pulsoescolar_api.entity.activity.Activity;
import pulsoescolar_api.entity.notification.UserNotification;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.repository.activity.ActivityRepository;
import pulsoescolar_api.repository.activity.QuizSubmissionRepository;
import pulsoescolar_api.repository.notification.UserNotificationRepository;

@Service @RequiredArgsConstructor
@Transactional
public class ActivityNotificationService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);
    private final ActivityRepository activities;
    private final QuizSubmissionRepository submissions;
    private final UserNotificationRepository notifications;
    private final Clock clock;

    public void opened(Activity activity) {
        if (activity.getClosesAt() == null || clock.instant().isBefore(activity.getClosesAt())) {
            notifyStudents(activity, false);
        }
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void remindClosingToday() {
        var now = clock.instant();
        var endOfDay = now.atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant();
        for (var activity : activities.findForClosingReminder(now, endOfDay)) {
            notifyStudents(activity, true);
            activity.setClosingReminderSent(true);
        }
    }

    private void notifyStudents(Activity activity, boolean closing) {
        var classroom = activity.getSubject().getClassroom();
        if (classroom.getDeletedAt() != null) return;
        var subject = activity.getSubject().getName();
        for (var student : activity.getRecipients()) {
            if (student.getRole() != Role.STUDENT || student.getDeletedAt() != null
                    || student.getActiveClassroom() == null
                    || !student.getActiveClassroom().getId().equals(classroom.getId())) continue;
            if (closing && submissions.existsByActivityIdAndStudentId(activity.getId(), student.getId())) continue;
            var notification = new UserNotification();
            notification.setRecipient(student);
            notification.setType(closing ? "QUIZ_CLOSING_TODAY" : "QUIZ_OPENED");
            notification.setTitle(closing ? "Atividade fecha hoje!" : "Novo quiz disponível!");
            notification.setMessage(closing
                    ? "A atividade de " + subject + " fecha hoje às " + TIME.format(activity.getClosesAt()) + "! Não deixe de responder."
                    : "Uma nova atividade de " + subject + " está disponível. Acesse e responda ao quiz!");
            notification.setCreatedAt(clock.instant());
            notifications.save(notification);
        }
    }
}
