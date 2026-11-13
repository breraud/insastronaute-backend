package fr.insastronaute.webapi.treasurehunt.service;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class VaultCodeGeneratorTest {
    @Test
    void generatesSixCharactersFromTheReadableAlphabet() {
        VaultCodeGenerator generator = new VaultCodeGenerator();

        assertThat(IntStream.range(0, 128).mapToObj(ignored -> generator.generate()))
                .allMatch(code -> code.matches("^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$"));
    }
}
