package fr.insastronaute.webapi.card;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway ne tourne pas dans les tests -- le schema y vient d'Hibernate -- donc rien
 * d'autre ne relit ce SQL avant la production.
 */
class CardDeletionMigrationTest {

    /**
     * Les deux tables de paliers refusent la cascade, et c'est porteur.
     *
     * <p>Elles portent leur position dans leur cle primaire et se lisent par
     * {@code @OrderColumn} : Hibernate comble les trous avec des null. Une ligne retiree
     * par la base laisse donc un trou, et la lecture du pack part en NPE. V134 avait mis
     * l'une des deux en cascade en croyant aligner une exception sur la regle ; V137 les
     * ramene toutes les deux, et le service detache la carte par JPA.</p>
     */
    @Test
    void anOrderedTierRefusesToLoseARowBeneathIt() throws IOException {
        Path migration =
                Path.of("src/main/resources/db/migration/V137__ordered_tier_cards_cannot_cascade.sql");
        assertThat(migration).exists();

        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("drop constraint deck_drop_tier_cards_card_id_fkey")
                .contains("drop constraint deck_tier_template_cards_card_id_fkey")
                .contains("references cards(id) on delete restrict");
        // Pas d'assertion « aucune cascade » : le commentaire de la migration en parle
        // pour expliquer ce qu'elle défait, et une recherche de texte ne sait pas
        // distinguer une explication d'une instruction.
        // Et la réparation des trous déjà créés, sans laquelle les paquets abîmés le
        // restent après le correctif.
        assertThat(sql).contains("row_number() over");
    }
}
