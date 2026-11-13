package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardInstance;
import fr.insastronaute.webapi.card.domain.CardInstanceReward;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CardInstanceRepositoryTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private CardInstanceRepository cardInstanceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CardInstanceRewardRepository rewardRepository;

    @Test
    void searchAdminWithoutFiltersReturnsInstances() {
        Card card = saveCard("Carte Alpha", "Rare", 7);
        CardInstance instance = saveInstance(card, "ABCDE-FGHJK-MNPQR");

        List<CardInstance> results = cardInstanceRepository.searchAdmin(null, null, null, null, null);

        assertThat(results).extracting(CardInstance::getId).containsExactly(instance.getId());
        assertThat(results.getFirst().getCard().getName()).isEqualTo("Carte Alpha");
    }

    @Test
    void searchAdminMatchesTypeAgainstCategoryAndName() {
        Card rareCard = saveCard("Carte Alpha", "Rare", 7);
        Card namedCard = saveCard("Carte Bravo", "Commune", 8);
        CardInstance rareInstance = saveInstance(rareCard, "AAAAA-BBBBB-CCCCC");
        CardInstance namedInstance = saveInstance(namedCard, "DDDDD-EEEEE-FFFFF");

        List<CardInstance> categoryResults = cardInstanceRepository.searchAdmin(null, null, null, "rArE", null);
        List<CardInstance> nameResults = cardInstanceRepository.searchAdmin(null, null, null, "bravo", null);

        assertThat(categoryResults).extracting(CardInstance::getId).containsExactly(rareInstance.getId());
        assertThat(nameResults).extracting(CardInstance::getId).containsExactly(namedInstance.getId());
    }

    @Test
    void listsAndCountsOnlyThePlayersCopiesOfTheCard() {
        Card card = saveCard("Carte Alpha", "Rare", 7);
        Card otherCard = saveCard("Carte Bravo", "Rare", 8);
        AppUser player = saveUser("player");
        AppUser other = saveUser("other");
        CardInstance later = saveOwnedInstance(card, "AAAAA-BBBBB-CCCCC", player, Instant.parse("2026-09-15T10:00:00Z"));
        CardInstance earlier = saveOwnedInstance(card, "DDDDD-EEEEE-FFFFF", player, Instant.parse("2026-09-01T10:00:00Z"));
        saveOwnedInstance(card, "GGGGG-HHHHH-JJJJJ", other, Instant.parse("2026-09-02T10:00:00Z"));
        saveOwnedInstance(otherCard, "KKKKK-LLLLL-MMMMM", player, Instant.parse("2026-09-03T10:00:00Z"));
        saveInstance(card, "NNNNN-PPPPP-QQQQQ");

        assertThat(cardInstanceRepository.findByOwnerIdAndCardIdOrderByAssignedAtAsc(player.getId(), card.getId()))
                .extracting(CardInstance::getId)
                .containsExactly(earlier.getId(), later.getId());
        assertThat(cardInstanceRepository.countByOwnerIdAndCardId(player.getId(), card.getId())).isEqualTo(2);
    }

    @Test
    void remembersWhichCopyAlreadyPaidWhichPlayerForWhichCard() {
        Card card = saveCard("Carte Alpha", "Rare", 7);
        Card otherCard = saveCard("Carte Bravo", "Rare", 8);
        AppUser player = saveUser("player");
        AppUser other = saveUser("other");
        CardInstance copy = saveInstance(card, "AAAAA-BBBBB-CCCCC");

        rewardRepository.saveAndFlush(new CardInstanceReward(copy.getId(), player.getId(), card.getId()));

        assertThat(rewardRepository.existsByCardInstanceIdAndUserIdAndCardId(copy.getId(), player.getId(), card.getId())).isTrue();
        assertThat(rewardRepository.existsByCardInstanceIdAndUserIdAndCardId(copy.getId(), other.getId(), card.getId())).isFalse();
        // Repointe vers une autre carte, l'exemplaire est une acquisition nouvelle.
        assertThat(rewardRepository.existsByCardInstanceIdAndUserIdAndCardId(copy.getId(), player.getId(), otherCard.getId())).isFalse();
        assertThat(rewardRepository.findAll().getFirst().getRewardedAt()).isNotNull();
    }

    private AppUser saveUser(String name) {
        AppUser user = new AppUser();
        user.setKeycloakSub(name + "-sub");
        user.setEmail(name + "@example.com");
        return userRepository.saveAndFlush(user);
    }

    private CardInstance saveOwnedInstance(Card card, String publicId, AppUser owner, Instant assignedAt) {
        CardInstance instance = new CardInstance();
        instance.setCard(card);
        instance.setPublicId(publicId);
        instance.setOwner(owner);
        instance.setAssignedAt(assignedAt);
        return cardInstanceRepository.saveAndFlush(instance);
    }

    private Card saveCard(String name, String category, int displayOrder) {
        Card card = new Card();
        card.setName(name);
        card.setCategory(category);
        card.setEnabled(true);
        card.setDisplayOrder(displayOrder);
        card.setImageObjectKey("cards/" + displayOrder + "/original.jpg");
        card.setThumbnailObjectKey("cards/" + displayOrder + "/thumbnail.jpg");
        card.setImageUrl("https://s3.example.com/cards/" + displayOrder + "/original.jpg");
        card.setThumbnailUrl("https://s3.example.com/cards/" + displayOrder + "/thumbnail.jpg");
        card.setContentType("image/jpeg");
        return cardRepository.saveAndFlush(card);
    }

    private CardInstance saveInstance(Card card, String publicId) {
        CardInstance instance = new CardInstance();
        instance.setCard(card);
        instance.setPublicId(publicId);
        return cardInstanceRepository.saveAndFlush(instance);
    }
}
