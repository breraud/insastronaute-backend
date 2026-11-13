package fr.insastronaute.webapi.card.service;

import fr.insastronaute.webapi.card.domain.Card;
import fr.insastronaute.webapi.card.domain.CardType;
import fr.insastronaute.webapi.card.dto.CardTypeRequest;
import fr.insastronaute.webapi.card.repository.CardRepository;
import fr.insastronaute.webapi.card.repository.CardTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardTypeServiceTest {

    @Mock CardTypeRepository typeRepository;
    @Mock CardRepository cardRepository;
    @InjectMocks CardTypeService typeService;

    @Test
    void createsTrimmedType() {
        when(typeRepository.save(any(CardType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = typeService.create(new CardTypeRequest("  Personnage  "));

        assertThat(result.name()).isEqualTo("Personnage");
    }

    @Test
    void deletingTypeClearsAssignedCards() {
        UUID typeId = UUID.randomUUID();
        CardType type = new CardType();
        type.setId(typeId);
        type.setName("Personnage");
        Card card = new Card();
        card.setTypeRef(type);
        when(typeRepository.findById(typeId)).thenReturn(Optional.of(type));
        when(cardRepository.findByTypeRef_Id(typeId)).thenReturn(List.of(card));

        typeService.delete(typeId);

        assertThat(card.getTypeRef()).isNull();
        verify(cardRepository).saveAll(List.of(card));
        verify(typeRepository).delete(type);
    }
}
