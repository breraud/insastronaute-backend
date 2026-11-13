package fr.insastronaute.webapi.treasurehunt.service;

import fr.insastronaute.webapi.treasurehunt.domain.TreasureHuntInputTool;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TreasureHuntSecretCodecTest {
    private static final int LONG_MELODY_NOTES = 100;

    // Deux fronts distincts saisissent la meme melodie : ils doivent produire le meme
    // secret, donc le meme hash. La forme canonique vit ici et nulle part ailleurs.
    @Test
    void foldsEveryWritingOfTheSameMelodyOntoOneCanonicalForm() {
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "re fa sol"))
                .contains("RE-FA-SOL");
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "RÉ, FA, SOL"))
                .contains("RE-FA-SOL");
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "  RE-FA-SOL  "))
                .contains("RE-FA-SOL");
    }

    // La specification enumere trois separateurs : le tiret, l'espace et la virgule. Tout
    // autre caractere fait partie du jeton, et un jeton qui n'est pas une note est refuse.
    // Le panneau d'administration decoupe une melodie de la meme facon : un separateur de
    // plus d'un cote annoncerait une longueur que l'autre ne lit pas.
    @Test
    void splitsOnTheThreeSeparatorsTheSpecificationNames() {
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "DO-RE MI,FA"))
                .contains("DO-RE-MI-FA");
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "DO;RE")).isEmpty();
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "DO/RE")).isEmpty();
    }

    @Test
    void foldsTheUsualFlatsOntoTheirSharp() {
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "REb SIb"))
                .contains("DO#-LA#");
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "RE♭ SI♭"))
                .contains("DO#-LA#");
    }

    // Un jeton qui n'est pas une note ne produit pas d'erreur a part : le secret est
    // simplement inexploitable, et suivra le chemin d'un secret inconnu.
    @Test
    void refusesAnythingThatIsNotOneOfTheTwelveNotes() {
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "DO H MI")).isEmpty();
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "DOb")).isEmpty();
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "   ")).isEmpty();
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, null)).isEmpty();
    }

    // Aucune longueur n'est imposee : une note seule est un secret valide, cent aussi.
    @Test
    void imposesNoLength() {
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "DO")).contains("DO");
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.PIANO, "DO ".repeat(LONG_MELODY_NOTES)))
                .contains(String.join("-", Collections.nCopies(LONG_MELODY_NOTES, "DO")));
    }

    @Test
    void refusesASecretFromAToolNoStepCouldEverExpect() {
        assertThat(TreasureHuntSecretCodec.normalize(null, "440")).isEmpty();
    }

    @Test
    void keepsTheHistoricalNormalisationForEveryOtherTool() {
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.KEYPAD, "  gm-42 "))
                .contains("GM-42");
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.TELEGRAPH, "...---"))
                .contains("...---");
        assertThat(TreasureHuntSecretCodec.normalize(TreasureHuntInputTool.KEYPAD, "  ")).isEmpty();
    }
}
