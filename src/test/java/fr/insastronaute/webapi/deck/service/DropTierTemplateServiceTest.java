package fr.insastronaute.webapi.deck.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.ConflictException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.deck.domain.DropTierTemplate;
import fr.insastronaute.webapi.deck.dto.DropTierCreateRequest;
import fr.insastronaute.webapi.deck.dto.DropTierDto;
import fr.insastronaute.webapi.deck.dto.DropTierTemplateDto;
import fr.insastronaute.webapi.deck.dto.DropTierTemplateRequest;
import fr.insastronaute.webapi.deck.repository.DropTierTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DropTierTemplateServiceTest {

    @Mock
    private DropTierTemplateRepository templateRepository;

    @Mock
    private CardRepository cardRepository;

    private DropTierTemplateService service;

    @BeforeEach
    void setUp() {
        service = new DropTierTemplateService(templateRepository, cardRepository);
    }

    @Test
    void savesTheArrangementInTheOrderItWasSent() {
        Card common = card("Commune");
        Card rare = card("Rare");
        when(cardRepository.findAllById(anyIterable())).thenReturn(List.of(common, rare));
        when(templateRepository.existsByNameIgnoreCase("Standard")).thenReturn(false);
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DropTierTemplateDto result = service.create(new DropTierTemplateRequest("  Standard  ", 3, List.of(
                tier("Commune", 70, common),
                tier("Rare", 30, rare)
        )));

        assertThat(result.name()).isEqualTo("Standard");
        assertThat(result.cardsPerPull()).isEqualTo(3);
        assertThat(result.dropTiers()).extracting(DropTierDto::label).containsExactly("Commune", "Rare");
        assertThat(result.dropTiers().getFirst().cards()).extracting(card -> card.name()).containsExactly("Commune");
    }

    @Test
    void refusesASecondTemplateUnderTheSameName() {
        when(templateRepository.existsByNameIgnoreCase("Standard")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new DropTierTemplateRequest("Standard", 1, List.of(
                tier("Commune", 100, card("Commune"))
        ))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Un modèle porte déjà ce nom.");
        verify(templateRepository, never()).save(any());
    }

    @Test
    void keepsItsOwnNameWhenRenamedToItself() {
        Card common = card("Commune");
        DropTierTemplate existing = new DropTierTemplate();
        existing.setId(UUID.randomUUID());
        existing.setName("Standard");
        when(templateRepository.findWithTiersById(existing.getId())).thenReturn(Optional.of(existing));
        when(templateRepository.findByNameIgnoreCase("Standard")).thenReturn(Optional.of(existing));
        when(cardRepository.findAllById(anyIterable())).thenReturn(List.of(common));
        when(templateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DropTierTemplateDto result = service.update(existing.getId(), new DropTierTemplateRequest(
                "Standard", 1, List.of(tier("Commune", 100, common))
        ));

        assertThat(result.name()).isEqualTo("Standard");
    }

    @Test
    void refusesAnArrangementThatCouldNotBeSavedAsAPack() {
        // Un modèle invalide serait un piège : on ne le découvrirait qu'en le
        // versant dans un pack, une fois le formulaire déjà rempli.
        assertThatThrownBy(() -> service.create(new DropTierTemplateRequest("Bancal", 1, List.of(
                tier("Commune", 70, card("Commune"))
        ))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La somme des pourcentages de drop doit être égale à 100.");
        verify(templateRepository, never()).save(any());
    }

    @Test
    void refusesGuaranteesThatDoNotFitTheDeclaredPull() {
        DropTierCreateRequest greedy = new DropTierCreateRequest("Rare", BigDecimal.valueOf(100), 3, 3, List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.create(new DropTierTemplateRequest("Trop", 1, List.of(greedy))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Les minimums garantis dépassent le nombre de cartes par tirage.");
    }

    @Test
    void refusesToDeleteATemplateThatIsNotThere() {
        UUID missing = UUID.randomUUID();
        when(templateRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(missing)).isInstanceOf(NotFoundException.class);
    }

    private DropTierCreateRequest tier(String label, int chance, Card card) {
        return new DropTierCreateRequest(label, BigDecimal.valueOf(chance), null, null, List.of(card.getId()));
    }

    private Card card(String name) {
        Card card = new Card();
        card.setId(UUID.randomUUID());
        card.setName(name);
        card.setCategory("Default");
        card.setEnabled(true);
        card.setDefaultCard(false);
        card.setDisplayOrder(0);
        card.setImageUrl("https://s3.example.com/cards/" + name + "/original.jpg");
        card.setThumbnailUrl("https://s3.example.com/cards/" + name + "/thumbnail.jpg");
        card.setContentType("image/jpeg");
        return card;
    }
}
