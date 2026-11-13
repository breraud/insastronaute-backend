package fr.insastronaute.webapi.sts.multiplayer.service;

import com.fasterxml.jackson.databind.JsonNode;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.leaderboard.service.LeaderboardService;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpDeck;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpMode;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;
import fr.insastronaute.webapi.sts.multiplayer.dto.StsPvpAdminDtos;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpBattleRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpDeckRepository;
import fr.insastronaute.webapi.sts.multiplayer.repository.StsPvpProfileRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que l'administration lit du multijoueur, pour savoir s'il faut équilibrer quelque chose.
 *
 * <p>Deux questions, et la page qui les pose va de l'une à l'autre : quels personnages se jouent
 * et lesquels gagnent, puis, pour les mieux classés, avec quels decks. Tout se lit dans ce qui est
 * déjà gardé — les batailles, les profils, les decks — et rien n'est écrit.
 */
@Service
public class StsPvpAdminService {

    /** Au-delà, une liste de joueurs cesse d'être une liste qu'on lit. */
    static final int MAX_PLAYER_LIMIT = 100;

    /**
     * Les formats qu'une statistique d'équilibrage compte par défaut.
     *
     * <p>Les raids en sont exclus : on y gagne contre un boss que le serveur pilote, et un
     * personnage qui brille en raid ne dit rien de ce qu'il vaut face à un autre personnage. Il
     * reste possible de les demander explicitement.
     */
    static final List<StsPvpMode> COMPETITIVE_MODES = List.of(StsPvpMode.ONE_V_ONE, StsPvpMode.TWO_V_TWO);

    private final StsPvpBattleRepository battleRepository;
    private final StsPvpProfileRepository profileRepository;
    private final StsPvpDeckRepository deckRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LeaderboardService leaderboardService;

    public StsPvpAdminService(
            StsPvpBattleRepository battleRepository,
            StsPvpProfileRepository profileRepository,
            StsPvpDeckRepository deckRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            LeaderboardService leaderboardService
    ) {
        this.battleRepository = battleRepository;
        this.profileRepository = profileRepository;
        this.deckRepository = deckRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.leaderboardService = leaderboardService;
    }

    /**
     * L'usage des personnages dans les batailles terminées.
     *
     * @param days            la période, en jours ; null ou négatif pour « depuis toujours »
     * @param mode            un seul format, ou null pour les formats compétitifs
     * @param includeFriendly si les duels amicaux comptent
     */
    @Transactional(readOnly = true)
    public StsPvpAdminDtos.CharacterUsage characterUsage(Integer days, String mode, boolean includeFriendly) {
        List<StsPvpMode> modes = modes(mode);
        Instant since = days == null || days <= 0
                ? null
                : TimeTravelContext.instantNow().minus(Duration.ofDays(days));

        List<StsPvpCharacterUsage.Battle> battles = new ArrayList<>();
        for (Object[] row : battleRepository.findFinishedTeamsAndWinners(
                since == null ? Instant.EPOCH : since, modes, includeFriendly)) {
            battles.add(new StsPvpCharacterUsage.Battle((JsonNode) row[0], (Integer) row[1]));
        }

        StsPvpCharacterUsage.Result result = StsPvpCharacterUsage.compute(battles);
        return new StsPvpAdminDtos.CharacterUsage(
                since,
                modes.stream().map(Enum::name).toList(),
                includeFriendly,
                result.battles(),
                result.picks(),
                result.characters().stream()
                        .map(row -> new StsPvpAdminDtos.CharacterUsageRow(
                                row.character(), row.picks(), row.share(),
                                row.wins(), row.losses(), row.draws(), row.winRate()))
                        .toList());
    }

    /** Les joueurs qui ont disputé un duel classé, du mieux classé au moins bien. */
    @Transactional(readOnly = true)
    public List<StsPvpAdminDtos.PlayerSummary> topPlayers(Integer limit) {
        int size = limit == null ? 20 : Math.max(1, Math.min(MAX_PLAYER_LIMIT, limit));
        Ranking ranking = ranking();
        Map<UUID, String> departments = departmentShortNames();

        List<StsPvpAdminDtos.PlayerSummary> players = new ArrayList<>();
        for (StsPvpProfile profile : ranking.ordered()) {
            if (players.size() >= size) {
                break;
            }
            AppUser user = profile.getUser();
            players.add(new StsPvpAdminDtos.PlayerSummary(
                    ranking.rankOf(user.getId()),
                    ranking.leaderboardRankOf(user.getId()),
                    user.getId(),
                    displayName(user),
                    departments.get(user.getDepartment()),
                    orZero(profile.getElo()),
                    orZero(profile.getWinsRanked()),
                    orZero(profile.getLossesRanked()),
                    orZero(profile.getWinsFriendly()),
                    orZero(profile.getLossesFriendly()),
                    profile.getSelectedCharacter()));
        }
        return players;
    }

    /**
     * Un joueur, son classement et tous ses decks.
     *
     * @throws NotFoundException quand ce joueur n'existe pas ou n'a jamais ouvert le multijoueur
     */
    @Transactional(readOnly = true)
    public StsPvpAdminDtos.PlayerDetail player(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Joueur introuvable."));
        StsPvpProfile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new NotFoundException("Ce joueur n'a pas de profil multijoueur."));

        Ranking ranking = ranking();
        List<StsPvpDeck> decks = deckRepository.findByOwner_IdOrderByUpdatedAtDesc(userId);
        UUID deckUsedInBattle = deckUsedInBattle(decks, profile.getSelectedCharacter());

        String character = profile.getSelectedCharacter();
        return new StsPvpAdminDtos.PlayerDetail(
                ranking.rankOf(userId),
                ranking.leaderboardRankOf(userId),
                userId,
                displayName(user),
                user.getEmail(),
                departmentShortNames().get(user.getDepartment()),
                orZero(profile.getElo()),
                orZero(profile.getWinsRanked()),
                orZero(profile.getLossesRanked()),
                orZero(profile.getWinsFriendly()),
                orZero(profile.getLossesFriendly()),
                character,
                character == null || character.isBlank() ? null : character.trim() + "Relic",
                decks.stream().map(deck -> toDeck(deck, deck.getId().equals(deckUsedInBattle))).toList());
    }

    /**
     * Le deck qu'un duel utiliserait aujourd'hui pour ce joueur.
     *
     * <p>La même règle que le combat : le deck marqué actif du personnage choisi, à défaut le plus
     * récent de ce personnage. L'écrire ici plutôt que de montrer le seul drapeau « actif » évite
     * à l'administration de regarder un deck que le combat n'utilise pas — un joueur sans deck
     * marqué actif joue quand même, avec son dernier deck modifié.
     */
    static UUID deckUsedInBattle(List<StsPvpDeck> decksMostRecentFirst, String selectedCharacter) {
        if (selectedCharacter == null || selectedCharacter.isBlank()) {
            return null;
        }
        StsPvpDeck mostRecent = null;
        for (StsPvpDeck deck : decksMostRecentFirst) {
            if (!selectedCharacter.equals(deck.getSelectedCharacter())) {
                continue;
            }
            if (Boolean.TRUE.equals(deck.getActive())) {
                return deck.getId();
            }
            if (mostRecent == null) {
                mostRecent = deck;
            }
        }
        return mostRecent == null ? null : mostRecent.getId();
    }

    /** Les cartes d'un deck, regroupées par identifiant, dans l'ordre de leur première apparition. */
    static List<StsPvpAdminDtos.DeckCard> groupCards(JsonNode cardIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (cardIds != null && cardIds.isArray()) {
            for (JsonNode cardId : cardIds) {
                String value = cardId.asText("").trim();
                if (!value.isEmpty()) {
                    counts.merge(value, 1, Integer::sum);
                }
            }
        }
        List<StsPvpAdminDtos.DeckCard> cards = new ArrayList<>();
        counts.forEach((cardId, count) -> cards.add(new StsPvpAdminDtos.DeckCard(cardId, count)));
        return cards;
    }

    private static StsPvpAdminDtos.Deck toDeck(StsPvpDeck deck, boolean usedInBattle) {
        List<StsPvpAdminDtos.DeckCard> cards = groupCards(deck.getCardIds());
        return new StsPvpAdminDtos.Deck(
                deck.getId(),
                deck.getName(),
                deck.getSelectedCharacter(),
                Boolean.TRUE.equals(deck.getActive()),
                usedInBattle,
                cards.stream().mapToInt(StsPvpAdminDtos.DeckCard::count).sum(),
                deck.getUpdatedAt(),
                cards);
    }

    private static List<StsPvpMode> modes(String mode) {
        if (mode == null || mode.isBlank()) {
            return COMPETITIVE_MODES;
        }
        try {
            return List.of(StsPvpMode.parse(mode));
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException(unknown.getMessage());
        }
    }

    /**
     * Les deux classements d'un coup : parmi tous les joueurs classés, et parmi ceux que le
     * classement public retient.
     */
    private Ranking ranking() {
        // Retrie dans l'ordre exact du classement public — classement, puis nom, puis identifiant.
        // La requete departage les egalites autrement, et deux joueurs a egalite auraient alors
        // echange leur place entre la liste de l'administration et le classement qu'elle sert a lire.
        List<StsPvpProfile> ordered = new ArrayList<>(profileRepository.findRankedByEloDesc(Pageable.unpaged()));
        ordered.sort(java.util.Comparator
                .comparingInt((StsPvpProfile profile) -> orZero(profile.getElo())).reversed()
                .thenComparing(profile -> displayName(profile.getUser()).toLowerCase())
                .thenComparing(profile -> profile.getUser().getId()));
        Map<UUID, Integer> ranks = new HashMap<>();
        Map<UUID, Integer> leaderboardRanks = new HashMap<>();
        int leaderboardPosition = 0;
        for (int index = 0; index < ordered.size(); index++) {
            AppUser user = ordered.get(index).getUser();
            ranks.put(user.getId(), index + 1);
            if (leaderboardService.isRankable(user)) {
                leaderboardRanks.put(user.getId(), ++leaderboardPosition);
            }
        }
        return new Ranking(ordered, ranks, leaderboardRanks);
    }

    private record Ranking(
            List<StsPvpProfile> ordered,
            Map<UUID, Integer> ranks,
            Map<UUID, Integer> leaderboardRanks
    ) {
        Integer rankOf(UUID userId) {
            return ranks.get(userId);
        }

        Integer leaderboardRankOf(UUID userId) {
            return leaderboardRanks.get(userId);
        }
    }

    private Map<UUID, String> departmentShortNames() {
        Map<UUID, String> names = new HashMap<>();
        for (Department department : departmentRepository.findAll()) {
            names.put(department.getId(), department.getShortName());
        }
        return names;
    }

    private static String displayName(AppUser user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : user.getEmail();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
