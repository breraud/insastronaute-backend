package fr.insastronaute.webapi.event.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.event.domain.Event;
import fr.insastronaute.webapi.event.domain.EventQuestion;
import fr.insastronaute.webapi.event.domain.EventQuestionOption;
import fr.insastronaute.webapi.event.domain.EventQuestionType;
import fr.insastronaute.webapi.event.domain.EventRegistration;
import fr.insastronaute.webapi.event.domain.EventRegistrationAnswer;
import fr.insastronaute.webapi.event.dto.EventAnswerDto;
import fr.insastronaute.webapi.event.dto.EventQuestionDto;
import fr.insastronaute.webapi.event.dto.EventQuestionOptionDto;
import fr.insastronaute.webapi.event.dto.EventQuestionOptionRequest;
import fr.insastronaute.webapi.event.dto.EventQuestionRequest;
import fr.insastronaute.webapi.event.dto.EventQuestionResultDto;
import fr.insastronaute.webapi.event.dto.EventQuestionnaireRequest;
import fr.insastronaute.webapi.event.dto.EventQuestionnaireResultsDto;
import fr.insastronaute.webapi.event.repository.EventQuestionRepository;
import fr.insastronaute.webapi.event.repository.EventRegistrationAnswerRepository;
import fr.insastronaute.webapi.event.repository.EventRegistrationRepository;
import fr.insastronaute.webapi.event.repository.EventRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Questions attached to an event, the answers registrants give, and the tallies
 * the organiser reads.
 */
@Service
public class EventQuestionnaireService {

    private final EventRepository eventRepository;
    private final EventQuestionRepository questionRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventRegistrationAnswerRepository answerRepository;

    public EventQuestionnaireService(
            EventRepository eventRepository,
            EventQuestionRepository questionRepository,
            EventRegistrationRepository registrationRepository,
            EventRegistrationAnswerRepository answerRepository
    ) {
        this.eventRepository = eventRepository;
        this.questionRepository = questionRepository;
        this.registrationRepository = registrationRepository;
        this.answerRepository = answerRepository;
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public List<EventQuestionDto> getQuestions(UUID eventId) {
        return questionRepository.findByEventIdOrderByPositionAsc(eventId).stream()
                .map(EventQuestionnaireService::toDto)
                .toList();
    }

    /**
     * Replaces the questionnaire in one call. A question or option that carries
     * an id is updated in place, so renaming "Végétarien" never discards the
     * answers pointing at it; anything left out is removed along with its
     * answers, which is the only honest reading of "the admin deleted it".
     */
    @Transactional
    public List<EventQuestionDto> replaceQuestionnaire(UUID eventId, EventQuestionnaireRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Événement introuvable."));

        List<EventQuestion> existing = questionRepository.findByEventIdOrderByPositionAsc(eventId);
        Map<UUID, EventQuestion> existingById = existing.stream()
                .collect(Collectors.toMap(EventQuestion::getId, Function.identity()));

        List<EventQuestionRequest> requested = request.questions() == null ? List.of() : request.questions();
        List<EventQuestion> kept = new ArrayList<>();
        int position = 0;

        for (EventQuestionRequest questionRequest : requested) {
            EventQuestion question = questionRequest.id() == null
                    ? new EventQuestion()
                    : requireOwned(existingById, questionRequest.id());
            question.setEvent(event);
            question.setLabel(questionRequest.label().trim());
            question.setType(questionRequest.type());
            question.setRequired(questionRequest.required());
            question.setPosition(position++);
            applyOptions(question, questionRequest);
            kept.add(question);
        }

        List<EventQuestion> removed = existing.stream()
                .filter(question -> kept.stream().noneMatch(k -> question.getId().equals(k.getId())))
                .toList();
        questionRepository.deleteAll(removed);
        questionRepository.saveAll(kept);

        return kept.stream().map(EventQuestionnaireService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public EventQuestionnaireResultsDto results(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Événement introuvable."));

        List<EventQuestion> questions = questionRepository.findByEventIdOrderByPositionAsc(eventId);
        long registrationCount = registrationRepository.findByEventIdOrderByStatusAscCreatedAtAsc(eventId).size();

        Map<UUID, Map<UUID, Long>> tallies = new HashMap<>();
        for (Object[] row : answerRepository.choiceTallies(eventId)) {
            tallies.computeIfAbsent((UUID) row[0], key -> new HashMap<>())
                    .put((UUID) row[1], ((Number) row[2]).longValue());
        }

        Map<UUID, Long> answeredByQuestion = answerRepository.answeredRegistrationCounts(eventId).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).longValue()));

        Map<UUID, List<EventQuestionResultDto.EventTextAnswerDto>> textsByQuestion = new LinkedHashMap<>();
        for (EventRegistrationAnswer answer : answerRepository.textAnswers(eventId)) {
            AppUser author = answer.getRegistration().getUser();
            textsByQuestion
                    .computeIfAbsent(answer.getQuestion().getId(), key -> new ArrayList<>())
                    .add(new EventQuestionResultDto.EventTextAnswerDto(displayName(author), answer.getTextValue()));
        }

        List<EventQuestionResultDto> results = questions.stream()
                .map(question -> toResult(question, tallies, answeredByQuestion, textsByQuestion, registrationCount))
                .toList();

        return new EventQuestionnaireResultsDto(event.getId(), event.getTitle(), registrationCount, results);
    }

    // ----------------------------------------------------------------- player

    /**
     * Validates and stores a registrant's answers. Called both when registering
     * and when a registrant revises them later, so the rules live in one place.
     */
    @Transactional
    public void saveAnswers(EventRegistration registration, List<EventAnswerDto> answers) {
        List<EventQuestion> questions = questionRepository
                .findByEventIdOrderByPositionAsc(registration.getEvent().getId());
        if (questions.isEmpty()) {
            return;
        }

        Map<UUID, EventAnswerDto> byQuestionId = new LinkedHashMap<>();
        for (EventAnswerDto answer : answers == null ? List.<EventAnswerDto>of() : answers) {
            if (answer.questionId() == null) {
                throw new BadRequestException("Une réponse ne désigne aucune question.");
            }
            byQuestionId.put(answer.questionId(), answer);
        }

        Map<UUID, EventRegistrationAnswer> existing = answerRepository
                .findByRegistrationId(registration.getId()).stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), Function.identity(), (a, b) -> a));

        List<EventRegistrationAnswer> toSave = new ArrayList<>();
        List<EventRegistrationAnswer> toDelete = new ArrayList<>();

        for (EventQuestion question : questions) {
            EventAnswerDto submitted = byQuestionId.remove(question.getId());
            EventRegistrationAnswer stored = existing.get(question.getId());

            if (submitted == null || isBlank(submitted)) {
                if (Boolean.TRUE.equals(question.getRequired())) {
                    throw new BadRequestException("« " + question.getLabel() + " » est obligatoire.");
                }
                // An answer cleared on purpose disappears rather than lingering
                // as a stale value nobody meant to keep.
                if (stored != null) {
                    toDelete.add(stored);
                }
                continue;
            }

            EventRegistrationAnswer answer = stored == null ? new EventRegistrationAnswer() : stored;
            answer.setRegistration(registration);
            answer.setQuestion(question);
            applyAnswer(question, submitted, answer);
            toSave.add(answer);
        }

        if (!byQuestionId.isEmpty()) {
            throw new BadRequestException("Une réponse porte sur une question qui n'appartient pas à cet événement.");
        }

        answerRepository.deleteAll(toDelete);
        answerRepository.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public List<EventAnswerDto> answersFor(UUID registrationId) {
        return answerRepository.findByRegistrationId(registrationId).stream()
                .map(answer -> new EventAnswerDto(
                        answer.getQuestion().getId(),
                        answer.getOption() == null ? null : answer.getOption().getId(),
                        answer.getTextValue()
                ))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private void applyAnswer(EventQuestion question, EventAnswerDto submitted, EventRegistrationAnswer answer) {
        if (question.getType() == EventQuestionType.SINGLE_CHOICE) {
            if (submitted.optionId() == null) {
                throw new BadRequestException("« " + question.getLabel() + " » attend un choix.");
            }
            EventQuestionOption option = question.getOptions().stream()
                    .filter(candidate -> candidate.getId().equals(submitted.optionId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "L'option choisie n'appartient pas à « " + question.getLabel() + " »."
                    ));
            answer.setOption(option);
            answer.setTextValue(null);
            return;
        }

        // The database enforces one shape or the other, so clearing the option
        // matters as much as setting the text.
        answer.setOption(null);
        answer.setTextValue(submitted.textValue().trim());
    }

    private void applyOptions(EventQuestion question, EventQuestionRequest request) {
        List<EventQuestionOptionRequest> requested =
                request.options() == null ? List.<EventQuestionOptionRequest>of() : request.options();

        if (request.type() == EventQuestionType.SINGLE_CHOICE && requested.size() < 2) {
            throw new BadRequestException("« " + request.label().trim() + " » doit proposer au moins deux options.");
        }
        if (request.type() == EventQuestionType.FREE_TEXT && !requested.isEmpty()) {
            throw new BadRequestException("Une question à texte libre ne prend pas d'options.");
        }

        Map<UUID, EventQuestionOption> existing = question.getOptions().stream()
                .filter(option -> option.getId() != null)
                .collect(Collectors.toMap(EventQuestionOption::getId, Function.identity()));

        List<EventQuestionOption> kept = new ArrayList<>();
        int position = 0;
        for (EventQuestionOptionRequest optionRequest : requested) {
            EventQuestionOption option = optionRequest.id() == null
                    ? new EventQuestionOption()
                    : Objects.requireNonNullElseGet(existing.get(optionRequest.id()), EventQuestionOption::new);
            option.setQuestion(question);
            option.setLabel(optionRequest.label().trim());
            option.setPosition(position++);
            kept.add(option);
        }

        question.getOptions().clear();
        question.getOptions().addAll(kept);
    }

    private EventQuestion requireOwned(Map<UUID, EventQuestion> existingById, UUID questionId) {
        EventQuestion question = existingById.get(questionId);
        if (question == null) {
            throw new BadRequestException("Une question modifiée n'appartient pas à cet événement.");
        }
        return question;
    }

    private static EventQuestionResultDto toResult(
            EventQuestion question,
            Map<UUID, Map<UUID, Long>> tallies,
            Map<UUID, Long> answeredByQuestion,
            Map<UUID, List<EventQuestionResultDto.EventTextAnswerDto>> textsByQuestion,
            long registrationCount
    ) {
        long answered = answeredByQuestion.getOrDefault(question.getId(), 0L);
        Map<UUID, Long> counts = tallies.getOrDefault(question.getId(), Map.of());

        List<EventQuestionResultDto.EventQuestionOptionResultDto> options = question.getOptions().stream()
                .sorted(Comparator.comparing(EventQuestionOption::getPosition))
                .map(option -> {
                    long count = counts.getOrDefault(option.getId(), 0L);
                    return new EventQuestionResultDto.EventQuestionOptionResultDto(
                            option.getId(), option.getLabel(), count, percentage(count, answered)
                    );
                })
                .toList();

        return new EventQuestionResultDto(
                question.getId(),
                question.getLabel(),
                question.getType(),
                Boolean.TRUE.equals(question.getRequired()),
                answered,
                Math.max(0L, registrationCount - answered),
                options,
                textsByQuestion.getOrDefault(question.getId(), List.of())
        );
    }

    /** Share of those who answered, not of those registered: an unanswered
     *  registration is reported separately rather than diluting every slice. */
    private static int percentage(long count, long answered) {
        if (answered <= 0 || count <= 0) {
            return 0;
        }
        return (int) Math.round((count * 100.0) / answered);
    }

    private static boolean isBlank(EventAnswerDto answer) {
        return answer.optionId() == null
                && (answer.textValue() == null || answer.textValue().isBlank());
    }

    private static String displayName(AppUser user) {
        String name = user.getDisplayName();
        return name == null || name.isBlank() ? user.getEmail() : name;
    }

    private static EventQuestionDto toDto(EventQuestion question) {
        return new EventQuestionDto(
                question.getId(),
                question.getLabel(),
                question.getType(),
                Boolean.TRUE.equals(question.getRequired()),
                question.getPosition(),
                question.getOptions().stream()
                        .sorted(Comparator.comparing(EventQuestionOption::getPosition))
                        .map(option -> new EventQuestionOptionDto(option.getId(), option.getLabel(), option.getPosition()))
                        .toList()
        );
    }
}
