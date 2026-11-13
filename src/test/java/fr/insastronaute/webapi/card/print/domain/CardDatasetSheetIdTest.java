package fr.insastronaute.webapi.card.print.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CardDatasetSheetIdTest {

    @Test
    void generatesAShortIdentifierThatCannotBeReadAsACardCode() {
        // Un code de carte est XXXXX-XXXXX-XXXXX sur 36 symboles. L'identifiant de planche
        // est imprime sur le meme papier et saisi par un humain : s'il pouvait ressembler a
        // un code de carte, une saisie maladroite designerait une carte au lieu d'une planche.
        String sheetId = CardDatasetSheetId.generate(new Random(1));

        assertThat(sheetId).matches("^PL-\\d{5}-\\d$");
        assertThat(sheetId).hasSize(10);
        assertThat(CardDatasetSheetId.isValid("ABCDE-FGHJK-MNPQR")).isFalse();
        assertThat(CardDatasetSheetId.isValid("PLABC-DEFGH-IJKLM")).isFalse();
    }

    @Test
    void acceptsWhatItGenerates() {
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            String sheetId = CardDatasetSheetId.generate(random);
            assertThat(CardDatasetSheetId.isValid(sheetId))
                    .as("identifiant genere %s", sheetId)
                    .isTrue();
        }
    }

    @Test
    void aSingleTypedCharacterNeverDesignatesAnotherSheet() {
        // C'est la seule propriete qui compte vraiment : l'identifiant est recopie a la main
        // depuis la feuille. Une faute de frappe doit etre refusee, jamais silencieusement
        // resolue vers une autre planche -- ce qui etiquetterait un lot entier avec les codes
        // d'une feuille qui n'est pas celle qu'on photographie.
        Random random = new Random(11);
        for (int attempt = 0; attempt < 50; attempt++) {
            String sheetId = CardDatasetSheetId.generate(random);
            for (int position = 0; position < sheetId.length(); position++) {
                char original = sheetId.charAt(position);
                if (!Character.isDigit(original)) {
                    continue;
                }
                for (char replacement = '0'; replacement <= '9'; replacement++) {
                    if (replacement == original) {
                        continue;
                    }
                    String mistyped = sheetId.substring(0, position) + replacement + sheetId.substring(position + 1);
                    assertThat(CardDatasetSheetId.isValid(mistyped))
                            .as("%s mistyped as %s", sheetId, mistyped)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void readsBackWhatAHumanActuallyTypes() {
        String sheetId = CardDatasetSheetId.generate(new Random(3));
        String digits = sheetId.replace("-", "");

        assertThat(CardDatasetSheetId.normalize(digits)).contains(sheetId);
        assertThat(CardDatasetSheetId.normalize(digits.toLowerCase())).contains(sheetId);
        assertThat(CardDatasetSheetId.normalize("  " + sheetId.toLowerCase() + " ")).contains(sheetId);
        assertThat(CardDatasetSheetId.normalize(sheetId.replace("-", " "))).contains(sheetId);
    }

    @Test
    void refusesWhatIsNotASheetIdentifier() {
        assertThat(CardDatasetSheetId.normalize(null)).isEmpty();
        assertThat(CardDatasetSheetId.normalize("")).isEmpty();
        assertThat(CardDatasetSheetId.normalize("PL-1234-5")).isEmpty();
        assertThat(CardDatasetSheetId.normalize("TEST-DATASET-V1")).isEmpty();
        Optional<String> cardCodeShaped = CardDatasetSheetId.normalize("ABCDE-FGHJK-MNPQR");
        assertThat(cardCodeShaped).isEmpty();
    }

    @Test
    void spreadsIdentifiersInsteadOfHandingOutNeighbours() {
        Random random = new Random(29);
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            generated.add(CardDatasetSheetId.generate(random));
        }
        assertThat(generated).hasSizeGreaterThan(450);
    }
}
