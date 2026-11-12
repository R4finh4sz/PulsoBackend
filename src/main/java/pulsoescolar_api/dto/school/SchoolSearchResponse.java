package pulsoescolar_api.dto.school;

import java.util.List;
import org.springframework.data.domain.Page;
import pulsoescolar_api.entity.school.School;

public record SchoolSearchResponse(List<SchoolOption> content, int page, int size,
        long totalElements, int totalPages) {
    public record SchoolOption(String id, String name, String city, String state, String cep) {
        public static SchoolOption from(School school) {
            return new SchoolOption(school.getId().toString(), school.getNome(), school.getCidade(),
                    school.getUf(), school.getCep());
        }
    }
    public static SchoolSearchResponse from(Page<School> result) {
        return new SchoolSearchResponse(result.map(SchoolOption::from).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
