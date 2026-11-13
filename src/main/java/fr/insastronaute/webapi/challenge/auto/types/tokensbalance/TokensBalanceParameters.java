package fr.insastronaute.webapi.challenge.auto.types.tokensbalance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TokensBalanceParameters(long amount) {

    @JsonCreator
    public TokensBalanceParameters(@JsonProperty("amount") Long amount) {
        this(normalize(amount));
    }

    private static long normalize(Long amount) {
        long value = amount == null ? 1L : amount;
        if (value < 1L) {
            throw new IllegalArgumentException("Le solde visé doit être au moins 1.");
        }
        return value;
    }
}
