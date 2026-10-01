package pulsoescolar_api.dto.school;

public record SchoolResponse(Long id, String nome, String cnpj, String logradouro, String bairro, String cidade,
        String cep, String uf, Coordinator coordinator) {
    public record Coordinator(Long id, String fullName, String email) {}
}
