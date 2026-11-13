package fr.insastronaute.webapi.common.adminaudit.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionEventDto;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionScope;
import fr.insastronaute.webapi.common.adminaudit.dto.AdminActionTypeDto;
import fr.insastronaute.webapi.common.adminaudit.repository.AdminActionEventRepository;
import fr.insastronaute.webapi.common.web.PageResponse;
import fr.insastronaute.webapi.user.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La lecture du registre. Rien ici n'ecrit : la table est en ajout seul, et le
 * seul chemin vers elle est {@link AdminActionJournal}.
 */
@Service
public class AdminActionEventQueryService {

    private static final Logger log = LoggerFactory.getLogger(AdminActionEventQueryService.class);

    private static final TypeReference<LinkedHashMap<String, Object>> DETAILS_TYPE = new TypeReference<>() {};

    private final AdminActionEventRepository events;
    private final ObjectMapper objectMapper;

    public AdminActionEventQueryService(AdminActionEventRepository events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    /**
     * Une page du registre, du plus recent au plus ancien.
     *
     * <p>Le tri n'est pas negociable : un registre se lit dans son ordre, et
     * le laisser choisir au client ouvrirait un tri sur des colonnes qui ne
     * veulent rien dire ici. Le {@code sort} recu est donc ignore, et seuls le
     * numero et la taille de page sont repris.</p>
     *
     * <p>La portee et la liste d'actions sont resolues en un seul ensemble de
     * types avant la requete : c'est le meme axe, exprime deux fois. Une
     * intersection vide - demander la portee globale et une action nominative
     * - est une page vide, rendue sans toucher la base.</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminActionEventDto> search(
            UUID actorUserId,
            UUID targetUserId,
            Collection<AdminActionType> actions,
            AdminActionScope scope,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        Pageable chronological = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Set<AdminActionType> requestedTypes = requestedTypes(actions, scope);
        if (requestedTypes.isEmpty()) {
            return PageResponse.from(Page.empty(chronological));
        }

        Page<AdminActionEvent> page = events.search(
                requestedTypes, actorUserId, targetUserId, from, to, chronological);
        return PageResponse.from(page.map(this::toDto));
    }

    /** Le catalogue que la barre de filtres affiche, dans l'ordre de l'enumeration. */
    public List<AdminActionTypeDto> actionTypes() {
        return Arrays.stream(AdminActionType.values()).map(AdminActionTypeDto::from).toList();
    }

    private Set<AdminActionType> requestedTypes(Collection<AdminActionType> actions, AdminActionScope scope) {
        AdminActionScope effectiveScope = scope == null ? AdminActionScope.ALL : scope;
        EnumSet<AdminActionType> types = EnumSet.allOf(AdminActionType.class);
        types.removeIf(action -> !effectiveScope.accepts(action));
        if (actions != null && !actions.isEmpty()) {
            types.retainAll(actions);
        }
        return types;
    }

    /**
     * L'auteur et la cible sont deja charges par les {@code join fetch} de la
     * requete : lire leur nom ici ne declenche aucune requete supplementaire.
     */
    private AdminActionEventDto toDto(AdminActionEvent event) {
        return new AdminActionEventDto(
                event.getId(),
                event.getOccurredAt(),
                user(event.getActor()),
                user(event.getTarget()),
                event.getAction(),
                event.getAction().isGlobalScope(),
                parseDetails(event),
                event.isBackfilled()
        );
    }

    private AdminActionEventDto.AdminActionUser user(AppUser user) {
        return user == null ? null : new AdminActionEventDto.AdminActionUser(user.getId(), user.getDisplayName());
    }

    /**
     * Les details relus. Toute ligne de production a ete ecrite depuis une
     * {@code Map} par {@link AdminActionJournal}, donc ce parsage ne peut pas
     * echouer sur elles ; s'il echoue quand meme, la table est en ajout seul et
     * la ligne fautive est irreparable - la faire echouer toute la page
     * n'aiderait personne, d'ou la trace et les details vides.
     */
    private Map<String, Object> parseDetails(AdminActionEvent event) {
        String json = event.getDetailsJson();
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, DETAILS_TYPE);
        } catch (Exception exception) {
            log.warn("Details illisibles sur la ligne de registre {}", event.getId(), exception);
            return Map.of();
        }
    }
}
