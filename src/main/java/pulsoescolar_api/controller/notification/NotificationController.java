package pulsoescolar_api.controller.notification;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import pulsoescolar_api.dto.notification.NotificationResponse;
import pulsoescolar_api.service.notification.NotificationService;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController @RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationResponse> mine() { return service.mine(); }

    @PatchMapping("/{id}/read") @ResponseStatus(NO_CONTENT)
    public void read(@PathVariable Long id) { service.read(id); }
}
