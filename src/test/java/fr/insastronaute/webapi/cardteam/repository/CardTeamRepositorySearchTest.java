package fr.insastronaute.webapi.cardteam.repository;

import fr.insastronaute.webapi.cardteam.domain.CardTeam;
import fr.insastronaute.webapi.cardteam.domain.CardTeamMember;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La recherche d'equipes, requetee pour de vrai.
 *
 * <p>Elle passait un parametre nu a {@code concat} : Postgres le typait en
 * {@code bytea} et refusait la requete des la premiere frappe. Ces tests
 * verrouillent le comportement de la version qui construit le motif en Java.
 * Ils tournent sur H2, qui acceptait aussi la version cassee — ils ne
 * rejoueraient donc pas la panne elle-meme, seulement la semantique.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class CardTeamRepositorySearchTest {

    @Autowired
    private CardTeamRepository teamRepository;

    @Autowired
    private CardTeamMemberRepository memberRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void aBlankPatternKeepsEveryTeam() {
        team("Les Fusees", user("sub-a", "a@example.com", "Alice"));
        team("Les Cometes", user("sub-b", "b@example.com", "Bob"));

        assertThat(search("%")).extracting(CardTeam::getName).containsExactly("Les Cometes", "Les Fusees");
    }

    @Test
    void aTeamMatchesOnItsOwnNameWhateverTheCase() {
        team("Les Fusees", user("sub-a", "a@example.com", "Alice"));
        team("Les Cometes", user("sub-b", "b@example.com", "Bob"));

        assertThat(search("%fus%")).extracting(CardTeam::getName).containsExactly("Les Fusees");
    }

    @Test
    void aTeamMatchesOnTheNameOfOneOfItsMembers() {
        team("Les Fusees", user("sub-a", "a@example.com", "Alice"));
        team("Les Cometes", user("sub-b", "b@example.com", "Bob"));

        assertThat(search("%bob%")).extracting(CardTeam::getName).containsExactly("Les Cometes");
    }

    /**
     * Le joker echappe est cherche comme un caractere ordinaire : sans quoi un
     * administrateur qui tape {@code _} verrait remonter toute la base.
     */
    @Test
    void anEscapedWildcardIsMatchedLiterally() {
        team("Equipe_1", user("sub-a", "a@example.com", "Alice"));
        team("Les Cometes", user("sub-b", "b@example.com", "Bob"));

        assertThat(search("%!_%")).extracting(CardTeam::getName).containsExactly("Equipe_1");
    }

    private Page<CardTeam> search(String pattern) {
        return teamRepository.searchAdminTeams(pattern, PageRequest.of(0, 20, Sort.by("name").ascending()));
    }

    private AppUser user(String sub, String email, String displayName) {
        AppUser user = new AppUser();
        user.setKeycloakSub(sub);
        user.setEmail(email);
        user.setDisplayName(displayName);
        return userRepository.saveAndFlush(user);
    }

    private CardTeam team(String name, AppUser creator) {
        CardTeam team = new CardTeam();
        team.setName(name);
        team.setCreator(creator);
        CardTeam saved = teamRepository.saveAndFlush(team);

        CardTeamMember member = new CardTeamMember();
        member.setTeam(saved);
        member.setUser(creator);
        memberRepository.saveAndFlush(member);

        return saved;
    }
}
