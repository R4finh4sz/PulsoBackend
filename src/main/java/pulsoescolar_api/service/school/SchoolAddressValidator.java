package pulsoescolar_api.service.school;

import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pulsoescolar_api.dto.school.BrazilianState;
import pulsoescolar_api.exception.common.BusinessValidationException;
import pulsoescolar_api.exception.common.AddressLookupUnavailableException;

@Service
public class SchoolAddressValidator {
    private final RestClient client;

    public SchoolAddressValidator() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl("https://viacep.com.br")
                .requestFactory(factory).build();
    }

    SchoolAddressValidator(RestClient client) {
        this.client = client;
    }

    public void validate(String cep, BrazilianState uf, String cidade, String bairro, String logradouro) {
        if (cep == null || !cep.matches("[0-9]{5}-?[0-9]{3}") || uf == null) {
            throw new BusinessValidationException("Informe CEP e UF válidos para verificar o endereço.");
        }
        Address address;
        try {
            address = client.get().uri("/ws/{cep}/json/", cep.replace("-", ""))
                    .retrieve().body(Address.class);
        } catch (RestClientException ex) {
            throw new AddressLookupUnavailableException(ex);
        }
        if (address == null) throw new AddressLookupUnavailableException(null);
        if (Boolean.TRUE.equals(address.erro())) {
            throw new BusinessValidationException("CEP não encontrado no ViaCEP.");
        }
        if (address.uf() == null || address.uf().isBlank()
                || address.localidade() == null || address.localidade().isBlank()) {
            throw new AddressLookupUnavailableException(null);
        }
        compare("UF", uf.name(), address.uf());
        compare("cidade", cidade, address.localidade());
        compare("bairro", bairro, address.bairro());
        compare("logradouro", logradouro, address.logradouro());
    }

    private void compare(String field, String submitted, String expected) {
        if (submitted == null || submitted.isBlank()) {
            throw new BusinessValidationException("Informe " + field + ".");
        }
        if (expected != null && !expected.isBlank() && !normalize(submitted).equals(normalize(expected))) {
            throw new BusinessValidationException("O campo " + field + " não corresponde ao CEP informado.");
        }
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    record Address(Boolean erro, String logradouro, String bairro, String localidade, String uf) {}
}
