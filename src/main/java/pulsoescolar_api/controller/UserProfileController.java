package pulsoescolar_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.UserResponse;
import pulsoescolar_api.service.UserProfileService;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserProfileService service;

    @GetMapping
    public UserResponse me() {
        return service.me();
    }
}
