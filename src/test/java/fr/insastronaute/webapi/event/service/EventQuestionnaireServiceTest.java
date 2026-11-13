package fr.insastronaute.webapi.event.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.domain.EventQuestion;
import fr.insastronaute.webapi.event.domain.EventQuestionOption;
import fr.insastronaute.webapi.event.domain.EventQuestionType;
import fr.insastronaute.webapi.event.domain.EventRegistration;
import fr.insastronaute.webapi.event.domain.EventRegistrationAnswer;
import fr.insastronaute.webapi.event.dto.EventAnswerDto;
import fr.insastronaute.webapi.event.repository.EventQuestionRepository;
import fr.insastronaute.webapi.event.repository.EventRegistrationAnswerRepository;
import fr.insastronaute.webapi.event.repository.EventRegistrationRepository;
import fr.insastronaute.webapi.event.repository.EventRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventQuestionnaireServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventQuestionRepository questionRepository;

    @Mock
    private EventRegistrationRepository registrationRepository;

    @Mock
    private EventRegistrationAnswerRepository answerRepository;

    private EventQuestionnaireService service;

    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EventQuestionnaireService(
                eventRepository, questionRepository, registrationRepository, answerRepository
        );
        when(answerRepository.findByRegistrationId(any())).thenReturn(List.of());
    }

    @Test
    void aChoiceAnswerIsStoredAgainstItsOption() {
        EventQuestion question = choice("À pied ou en courant ?", true, "À pied", "En courant");
        stubQuestions(question);
        EventRegistration registration = registration();

        service.saveAnswers(registration, List.of(
                new EventAnswerDto(question.getId(), question.getOptions().get(1).getId(), null)
        ));

        assertThat(saved()).singleElement().satisfies(answer -> {
            assertThat(answer.getOption().getLabel()).isEqualTo("En courant");
            assertThat(answer.getTextValue()).isNull();
        });
    }

    @Test
    void aFreeTextAnswerClearsAnyOption() {
        EventQuestion question = text("Allergies ?", false);
        stubQuestions(question);

        service.saveAnswers(registration(), List.of(
                new EventAnswerDto(question.getId(), null, "  arachides  ")
        ));

        assertThat(saved()).singleElement().satisfies(answer -> {
            assertThat(answer.getOption()).isNull();
            // Trimmed: a stray space would split the tally of an otherwise
            // identical answer.
            assertThat(answer.getTextValue()).isEqualTo("arachides");
        });
    }

    @Test
    void aRequiredQuestionCannotBeSkipped() {
        EventQuestion question = choice("Régime ?", true, "Classique", "Végétarien");
        stubQuestions(question);

        assertThatThrownBy(() -> service.saveAnswers(registration(), List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("obligatoire");
    }

    @Test
    void anOptionalQuestionMayBeLeftBlank() {
        EventQuestion question = text("Remarque ?", false);
        stubQuestions(question);

        service.saveAnswers(registration(), List.of());

        verify(answerRepository).saveAll(List.of());
    }

    @Test
    void clearingAnOptionalAnswerDeletesItRatherThanKeepingAStaleValue() {
        EventQuestion question = text("Remarque ?", false);
        stubQuestions(question);
        EventRegistration registration = registration();
        EventRegistrationAnswer stored = new EventRegistrationAnswer();
        stored.setQuestion(question);
        stored.setRegistration(registration);
        stored.setTextValue("ancienne remarque");
        when(answerRepository.findByRegistrationId(registration.getId())).thenReturn(List.of(stored));

        service.saveAnswers(registration, List.of(new EventAnswerDto(question.getId(), null, "   ")));

        verify(answerRepository).deleteAll(List.of(stored));
    }

    @Test
    void anOptionFromAnotherQuestionIsRefused() {
        EventQuestion question = choice("Régime ?", true, "Classique", "Végétarien");
        stubQuestions(question);

        assertThatThrownBy(() -> service.saveAnswers(
                registration(), List.of(new EventAnswerDto(question.getId(), UUID.randomUUID(), null))
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("n'appartient pas");
    }

    @Test
    void aChoiceQuestionAnsweredWithTextIsRefused() {
        EventQuestion question = choice("Régime ?", true, "Classique", "Végétarien");
        stubQuestions(question);

        assertThatThrownBy(() -> service.saveAnswers(
                registration(), List.of(new EventAnswerDto(question.getId(), null, "végé"))
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("attend un choix");
    }

    @Test
    void anAnswerToAForeignQuestionIsRefused() {
        stubQuestions(text("Remarque ?", false));

        assertThatThrownBy(() -> service.saveAnswers(
                registration(), List.of(new EventAnswerDto(UUID.randomUUID(), null, "coucou"))
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("n'appartient pas à cet événement");
    }

    @Test
    void anEventWithoutQuestionsIgnoresAnswersEntirely() {
        when(questionRepository.findByEventIdOrderByPositionAsc(eventId)).thenReturn(List.of());

        service.saveAnswers(registration(), List.of(new EventAnswerDto(UUID.randomUUID(), null, "peu importe")));

        verify(answerRepository, never()).saveAll(any());
    }

    // ------------------------------------------------------------- fixtures

    private void stubQuestions(EventQuestion... questions) {
        when(questionRepository.findByEventIdOrderByPositionAsc(eventId)).thenReturn(List.of(questions));
    }

    @SuppressWarnings("unchecked")
    private List<EventRegistrationAnswer> saved() {
        ArgumentCaptor<List<EventRegistrationAnswer>> captor = ArgumentCaptor.forClass(List.class);
        verify(answerRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private EventRegistration registration() {
        Event event = new Event();
        event.setId(eventId);
        event.setTitle("Pique-nique Sainte Catherine");

        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setEmail("meca@example.com");

        EventRegistration registration = new EventRegistration();
        registration.setId(UUID.randomUUID());
        registration.setEvent(event);
        registration.setUser(user);
        return registration;
    }

    private EventQuestion choice(String label, boolean required, String... optionLabels) {
        EventQuestion question = base(label, required, EventQuestionType.SINGLE_CHOICE);
        int position = 0;
        for (String optionLabel : optionLabels) {
            EventQuestionOption option = new EventQuestionOption();
            option.setId(UUID.randomUUID());
            option.setQuestion(question);
            option.setLabel(optionLabel);
            option.setPosition(position++);
            question.getOptions().add(option);
        }
        return question;
    }

    private EventQuestion text(String label, boolean required) {
        return base(label, required, EventQuestionType.FREE_TEXT);
    }

    private EventQuestion base(String label, boolean required, EventQuestionType type) {
        EventQuestion question = new EventQuestion();
        question.setId(UUID.randomUUID());
        question.setLabel(label);
        question.setType(type);
        question.setRequired(required);
        return question;
    }
}
