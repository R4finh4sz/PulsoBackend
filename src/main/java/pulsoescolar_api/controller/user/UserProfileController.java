package pulsoescolar_api.controller.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pulsoescolar_api.dto.user.UserResponse;
import pulsoescolar_api.service.user.UserProfileService;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserProfileService service;
    private final pulsoescolar_api.service.user.UserManagementService management;

    @org.springframework.web.bind.annotation.PutMapping(value = "/photo", consumes = "multipart/form-data")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void updatePhoto(@org.springframework.web.bind.annotation.RequestParam("photo")
            org.springframework.web.multipart.MultipartFile photo) {
        service.updatePhoto(photo);
    }

    @org.springframework.web.bind.annotation.PatchMapping
    public UserResponse update(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            pulsoescolar_api.dto.user.UpdateUserRequest request) {
        return management.updateSelf(request);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/active-school")
    public UserResponse activeSchool(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody
            pulsoescolar_api.dto.user.ActiveSchoolRequest request) {
        return service.activeSchool(request.schoolId());
    }

    @GetMapping
    public UserResponse me() {
        return service.me();
    }

    @GetMapping("/profile")
    public pulsoescolar_api.dto.user.UserProfileResponse profile() {
        return service.profile();
    }

    @GetMapping(value = "/photo", produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    public org.springframework.http.ResponseEntity<byte[]> photo() {
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(service.photo());
    }
}
