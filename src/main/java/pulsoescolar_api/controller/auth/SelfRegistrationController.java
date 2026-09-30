package pulsoescolar_api.controller.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import pulsoescolar_api.dto.user.*;
import pulsoescolar_api.entity.user.Role;
import pulsoescolar_api.service.user.*;

@RestController
@RequestMapping("/api/auth/register")
@RequiredArgsConstructor
public class SelfRegistrationController {
    private final SelfRegistrationService service;
    private final RegistrationPhotoService photos;
    private final JsonMapper json;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationReceipt register(@Valid @RequestBody SelfRegistrationRequest input) {
        return submit(input, null);
    }

    // React Native FormData sends data as a plain string part, not application/json.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationReceipt multipart(@RequestParam("data") String data,
            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        SelfRegistrationRequest input;
        try {
            input = json.readValue(data, SelfRegistrationRequest.class);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados de cadastro inválidos.");
        }
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados do cadastro.");
        return submit(input, photos.read(photo));
    }

    private RegistrationReceipt submit(SelfRegistrationRequest input, byte[] photo) {
        return service.register(input, input.role() == null ? Role.STUDENT : input.role(), photo);
    }
}
