package pulsoescolar_api.dto.school;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum BrazilianState {
    AC, AL, AP, AM, BA, CE, DF, ES, GO, MA, MT, MS, MG, PA, PB, PR, PE, PI, RJ, RN, RS, RO, RR, SC, SP, SE, TO;

    @JsonCreator
    public static BrazilianState fromJson(String value) { return valueOf(value); }
}
