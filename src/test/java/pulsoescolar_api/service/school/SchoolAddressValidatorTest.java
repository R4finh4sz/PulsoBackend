package pulsoescolar_api.service.school;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pulsoescolar_api.dto.school.BrazilianState;
import pulsoescolar_api.exception.common.AddressLookupUnavailableException;
import pulsoescolar_api.exception.common.BusinessValidationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SchoolAddressValidatorTest {
    MockRestServiceServer server;
    SchoolAddressValidator validator;
    static final String ADDRESS = """
            {"cep":"01001-000","logradouro":"Praça da Sé","bairro":"Sé","localidade":"São Paulo","uf":"SP","ibge":"3550308"}
            """;

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://viacep.com.br");
        server = MockRestServiceServer.bindTo(builder).build();
        validator = new SchoolAddressValidator(builder.build());
    }

    void respond(String body) {
        server.expect(requestTo("https://viacep.com.br/ws/01001000/json/"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    void validate(String city, String district, String street) {
        validator.validate("01001-000", BrazilianState.SP, city, district, street);
    }

    @Test void acceptsEquivalentAddressAndIgnoresExtraResponseFields() {
        respond(ADDRESS);
        assertDoesNotThrow(() -> validate(" sao  paulo ", "SE", "Praca da Se"));
        server.verify();
    }

    @Test void rejectsEveryMismatchingField() {
        for (String field : new String[]{"São Paulo", "Sé", "Praça da Sé", "SP"}) {
            server.reset();
            respond(ADDRESS.replace("\"" + field + "\"", "\"Outro\""));
            assertThrows(BusinessValidationException.class,
                    () -> validate("São Paulo", "Sé", "Praça da Sé"));
            server.verify();
        }
    }

    @Test void genericCepAllowsManualStreetAndDistrictButStillChecksCity() {
        String generic = ADDRESS.replace("Praça da Sé", "").replace("\"Sé\"", "\"\"");
        respond(generic);
        assertDoesNotThrow(() -> validate("São Paulo", "Meu bairro", "Minha rua"));
        server.reset();
        respond(generic);
        assertThrows(BusinessValidationException.class, () -> validate("Recife", "Meu bairro", "Minha rua"));
    }

    @Test void missingStreetDoesNotDisableDistrictComparison() {
        respond(ADDRESS.replace("Praça da Sé", ""));
        assertThrows(BusinessValidationException.class, () -> validate("São Paulo", "Outro", "Minha rua"));
    }

    @Test void rejectsUnknownCep() {
        respond("{\"erro\":true}");
        assertThrows(BusinessValidationException.class, () -> validate("São Paulo", "Sé", "Praça da Sé"));
    }

    @Test void refusesUnverifiableResponses() {
        for (String body : new String[]{"", "{}", "{\"uf\":\"SP\"}", "invalid json"}) {
            server.reset();
            respond(body);
            assertThrows(AddressLookupUnavailableException.class, () -> validate("São Paulo", "Sé", "Praça da Sé"));
        }
    }

    @Test void upstreamFailureDoesNotAllowManualAddress() {
        server.expect(requestTo("https://viacep.com.br/ws/01001000/json/"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(AddressLookupUnavailableException.class, () -> validate("São Paulo", "Sé", "Praça da Sé"));
    }

    @Test void requiresCepAndStateBeforeLookup() {
        for (String cep : new String[]{null, "", "123", "01001000/"}) {
            assertThrows(BusinessValidationException.class,
                    () -> validator.validate(cep, BrazilianState.SP, "São Paulo", "Sé", "Praça da Sé"));
        }
        assertThrows(BusinessValidationException.class,
                () -> validator.validate("01001000", null, "São Paulo", "Sé", "Praça da Sé"));
        server.verify();
    }
}
