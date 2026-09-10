package pulsoescolar_api.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pulsoescolar_api.entity.Classroom;
import pulsoescolar_api.exception.ResourceNotFoundException;
import pulsoescolar_api.repository.ClassroomRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomLookupService {
    private final ClassroomRepository classrooms;

    public Classroom findById(Long id) {
        return classrooms.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sala não encontrada."));
    }
}
