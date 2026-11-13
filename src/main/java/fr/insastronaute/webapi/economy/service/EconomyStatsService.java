package fr.insastronaute.webapi.economy.service;

import fr.insastronaute.webapi.challenge.domain.Challenge;
import fr.insastronaute.webapi.challenge.domain.ChallengeSubmission;
import fr.insastronaute.webapi.challenge.repository.ChallengeSubmissionRepository;
import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.economy.domain.CurrencyMovement;
import fr.insastronaute.webapi.economy.domain.CurrencySource;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.Anomaly;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.Balances;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.Bucket;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.ChallengePayout;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.DayPoint;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.DepartmentStats;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.Distribution;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.Earner;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.GameRuns;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.SourceStats;
import fr.insastronaute.webapi.economy.dto.EconomyStatsDto.Totals;
import fr.insastronaute.webapi.economy.repository.CurrencyMovementRepository;
import fr.insastronaute.webapi.sts.repository.StsRunRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Les chiffres de l'onglet stats : d'ou viennent les tokens, et ce qui cloche.
 *
 * <p>Tout se calcule en memoire, a partir de quelques lectures. Une promo compte
 * quelques centaines de joueurs et quelques milliers de mouvements par mois :
 * c'est moins que ce qu'une requete d'agregation par graphique couterait en
 * allers-retours, et les regles d'anomalie se testent sans base.</p>
 *
 * <p>Les seuils d'anomalie reposent sur la mediane, jamais sur la moyenne ni le
 * maximum : sur de petits effectifs, l'ecart atypique qu'on cherche tire la
 * moyenne vers lui et se cache dans le seuil qu'il aurait du franchir.</p>
 */
@Service
public class EconomyStatsService {

    static final int MAX_DAYS = 90;
    static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    /** En dessous, un gain n'est jamais une anomalie : dix fois presque rien reste presque rien. */
    static final long ANOMALY_FLOOR = 50;
    /** Un ajustement admin de cette taille merite d'etre vu, meme legitime. */
    static final long ADMIN_ADJUSTMENT_ALERT = 500;
    /** « Dix fois ce qui est habituel » : lisible, et loin du bruit ordinaire. */
    static final long OUTLIER_FACTOR = 10;
    static final double DOMINANT_SOURCE_SHARE = 0.70;
    static final double CONCENTRATION_SHARE = 0.30;

    private static final long[] BUCKET_EDGES = {0, 1, 10, 50, 100, 250, 500, 1_000, 2_500, 5_000};
    private static final int MAX_ANOMALIES = 60;

    private final CurrencyMovementRepository movementRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ChallengeSubmissionRepository submissionRepository;
    private final StsRunRepository runRepository;

    public EconomyStatsService(
            CurrencyMovementRepository movementRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            ChallengeSubmissionRepository submissionRepository,
            StsRunRepository runRepository
    ) {
        this.movementRepository = movementRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.submissionRepository = submissionRepository;
        this.runRepository = runRepository;
    }

    @Transactional(readOnly = true)
    public EconomyStatsDto stats(int requestedDays) {
        int days = Math.max(1, Math.min(MAX_DAYS, requestedDays));
        Instant to = TimeTravelContext.instantNow();
        Instant from = to.minus(Duration.ofDays(days));

        List<CurrencyMovement> window = movementRepository.findInWindow(from, to);
        List<AppUser> players = userRepository.findAll();
        Map<UUID, AppUser> playersById = players.stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity(), (a, b) -> a));
        Map<UUID, Department> departmentsById = departmentRepository.findAllByVirtualFalse().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity(), (a, b) -> a));

        List<Anomaly> anomalies = new ArrayList<>();
        Totals totals = totals(window);
        List<SourceStats> sources = sources(window, totals.tokensIn(), playersById, anomalies);
        flagDominantSource(sources, totals.tokensIn(), anomalies);

        List<DayPoint> daily = daily(window, from, to, anomalies);
        List<Earner> topEarners = topEarners(window, totals.tokensIn(), playersById, departmentsById);
        List<DepartmentStats> departments = departments(window, players, departmentsById);
        Balances balances = balances(players, anomalies);
        List<ChallengePayout> challengePayouts = challengePayouts();
        GameRuns gameRuns = gameRuns(playersById, anomalies);

        anomalies.sort(Comparator
                .comparingInt((Anomaly anomaly) -> severityRank(anomaly.severity()))
                .thenComparing(Comparator.comparingLong(Anomaly::value).reversed()));
        List<Anomaly> shown = anomalies.size() > MAX_ANOMALIES ? anomalies.subList(0, MAX_ANOMALIES) : anomalies;

        return new EconomyStatsDto(from, to, days, totals, sources, daily, topEarners, departments,
                balances, challengePayouts, gameRuns, List.copyOf(shown));
    }

    // ── Flux ────────────────────────────────────────────────────────────────────

    private static Totals totals(List<CurrencyMovement> window) {
        long tokensIn = 0, tokensOut = 0, eventPointsIn = 0, eventPointsOut = 0;
        Set<UUID> active = new HashSet<>();
        for (CurrencyMovement movement : window) {
            tokensIn += Math.max(0, movement.getTokenDelta());
            tokensOut += Math.max(0, -movement.getTokenDelta());
            eventPointsIn += Math.max(0, movement.getEventPointsDelta());
            eventPointsOut += Math.max(0, -movement.getEventPointsDelta());
            active.add(movement.getUserId());
        }
        return new Totals(tokensIn, tokensOut, eventPointsIn, eventPointsOut, active.size(), window.size());
    }

    private List<SourceStats> sources(List<CurrencyMovement> window, long totalInflow,
                                      Map<UUID, AppUser> playersById, List<Anomaly> anomalies) {
        Map<CurrencySource, List<CurrencyMovement>> bySource = new EnumMap<>(CurrencySource.class);
        for (CurrencyMovement movement : window) {
            bySource.computeIfAbsent(movement.getSource(), ignored -> new ArrayList<>()).add(movement);
        }

        List<SourceStats> stats = new ArrayList<>();
        for (CurrencySource source : CurrencySource.values()) {
            List<CurrencyMovement> movements = bySource.getOrDefault(source, List.of());
            long tokensIn = 0, tokensOut = 0, eventPointsIn = 0, eventPointsOut = 0, expectedEventPoints = 0;
            Map<UUID, Long> gainPerPlayer = new HashMap<>();
            Set<UUID> players = new HashSet<>();
            List<Long> perAction = new ArrayList<>();

            for (CurrencyMovement movement : movements) {
                long tokens = movement.getTokenDelta();
                long eventPoints = movement.getEventPointsDelta();
                tokensIn += Math.max(0, tokens);
                tokensOut += Math.max(0, -tokens);
                eventPointsIn += Math.max(0, eventPoints);
                eventPointsOut += Math.max(0, -eventPoints);
                expectedEventPoints += Math.max(0, source.eventPointsFor(tokens));
                players.add(movement.getUserId());
                if (tokens > 0) {
                    gainPerPlayer.merge(movement.getUserId(), tokens, Long::sum);
                }
                // Ce que « vaut une action » : un gain pour les sources qui rapportent,
                // un cout pour celles qui depensent — en tokens, ou en PE pour Place.
                long action = tokens != 0 ? Math.abs(tokens) : Math.abs(eventPoints);
                if (action > 0) {
                    perAction.add(action);
                }
            }

            // Le registre ne peut pas creer plus de PE que la regle n'en donne : les
            // plafonds a zero n'en retirent que. S'il y en a davantage, un credit
            // contourne CurrencySource, et c'est une fuite de monnaie.
            if (eventPointsIn > expectedEventPoints) {
                anomalies.add(new Anomaly("EVENT_POINTS_OUT_OF_RULE", "CRITICAL",
                        "PE créés hors règle",
                        "La source " + source.name() + " a crédité " + eventPointsIn + " PE, alors que ses tokens n'en justifient que "
                                + expectedEventPoints + ". Un crédit contourne la règle des points Events.",
                        null, null, source.name(), null, eventPointsIn, expectedEventPoints));
            }

            flagOutlierActions(source, movements, playersById, anomalies);
            flagPlayerDaySpikes(source, movements, playersById, anomalies);
            if (source == CurrencySource.ADMIN_ADJUSTMENT) {
                flagLargeAdjustments(movements, playersById, anomalies);
            }

            stats.add(new SourceStats(source.name(), tokensIn, tokensOut, eventPointsIn, eventPointsOut,
                    movements.size(), players.size(),
                    totalInflow == 0 ? 0 : (double) tokensIn / totalInflow,
                    distribution(perAction), distribution(new ArrayList<>(gainPerPlayer.values()))));
        }
        return stats;
    }

    /** Une seule action qui rapporte dix fois ce qu'une action rapporte d'habitude. */
    private static void flagOutlierActions(CurrencySource source, List<CurrencyMovement> movements,
                                           Map<UUID, AppUser> playersById, List<Anomaly> anomalies) {
        List<CurrencyMovement> gains = movements.stream().filter(movement -> movement.getTokenDelta() > 0).toList();
        if (gains.size() < 20) {
            return;
        }
        long median = percentile(sorted(gains.stream().map(CurrencyMovement::getTokenDelta).toList()), 50);
        long threshold = Math.max(ANOMALY_FLOOR, median * OUTLIER_FACTOR);
        gains.stream()
                .filter(movement -> movement.getTokenDelta() > threshold)
                .sorted(Comparator.comparingLong(CurrencyMovement::getTokenDelta).reversed())
                .limit(5)
                .forEach(movement -> anomalies.add(new Anomaly("OUTLIER_ACTION", "WARNING",
                        "Gain isolé très au-dessus de la normale",
                        "Une seule action " + source.name() + " a rapporté " + movement.getTokenDelta()
                                + " tokens, plus de " + OUTLIER_FACTOR + " fois le gain habituel (" + median + ").",
                        movement.getUserId(), nameOf(playersById, movement.getUserId()), source.name(),
                        dayOf(movement.getOccurredAt()).toString(), movement.getTokenDelta(), threshold)));
    }

    /** Un joueur qui gagne en un jour dix fois ce qu'un joueur gagne d'habitude par cette source. */
    private static void flagPlayerDaySpikes(CurrencySource source, List<CurrencyMovement> movements,
                                            Map<UUID, AppUser> playersById, List<Anomaly> anomalies) {
        Map<UUID, Map<LocalDate, Long>> gains = new HashMap<>();
        for (CurrencyMovement movement : movements) {
            if (movement.getTokenDelta() > 0) {
                gains.computeIfAbsent(movement.getUserId(), ignored -> new HashMap<>())
                        .merge(dayOf(movement.getOccurredAt()), movement.getTokenDelta(), Long::sum);
            }
        }
        List<Long> playerDays = gains.values().stream().flatMap(days -> days.values().stream()).toList();
        if (playerDays.size() < 10) {
            return;
        }
        long median = percentile(sorted(playerDays), 50);
        long threshold = Math.max(ANOMALY_FLOOR, median * OUTLIER_FACTOR);
        gains.forEach((userId, days) -> days.forEach((day, gain) -> {
            if (gain > threshold) {
                anomalies.add(new Anomaly("PLAYER_DAY_SPIKE", "WARNING",
                        "Gain journalier anormal",
                        nameOf(playersById, userId) + " a gagné " + gain + " tokens en une journée par " + source.name()
                                + ", plus de " + OUTLIER_FACTOR + " fois ce qu'un joueur en tire d'habitude (" + median + ").",
                        userId, nameOf(playersById, userId), source.name(), day.toString(), gain, threshold));
            }
        }));
    }

    private static void flagLargeAdjustments(List<CurrencyMovement> movements, Map<UUID, AppUser> playersById,
                                             List<Anomaly> anomalies) {
        for (CurrencyMovement movement : movements) {
            long amount = Math.abs(movement.getTokenDelta());
            if (amount >= ADMIN_ADJUSTMENT_ALERT) {
                anomalies.add(new Anomaly("LARGE_ADMIN_ADJUSTMENT", "INFO",
                        "Ajustement admin important",
                        (movement.getTokenDelta() > 0 ? "Crédit" : "Retrait") + " manuel de " + amount + " tokens pour "
                                + nameOf(playersById, movement.getUserId()) + ". Légitime peut-être, mais à connaître.",
                        movement.getUserId(), nameOf(playersById, movement.getUserId()),
                        CurrencySource.ADMIN_ADJUSTMENT.name(), dayOf(movement.getOccurredAt()).toString(),
                        amount, ADMIN_ADJUSTMENT_ALERT));
            }
        }
    }

    /** Une source qui rapporte presque tout : les autres ne pesent plus, c'est un desequilibre. */
    private static void flagDominantSource(List<SourceStats> sources, long totalInflow, List<Anomaly> anomalies) {
        if (totalInflow < 500) {
            return;
        }
        for (SourceStats source : sources) {
            if (source.shareOfInflow() > DOMINANT_SOURCE_SHARE) {
                anomalies.add(new Anomaly("DOMINANT_SOURCE", "WARNING",
                        "Une source écrase les autres",
                        source.source() + " représente " + Math.round(source.shareOfInflow() * 100)
                                + " % des tokens gagnés. Les autres activités ne pèsent presque plus dans l'économie.",
                        null, null, source.source(), null, source.tokensIn(), Math.round(totalInflow * DOMINANT_SOURCE_SHARE)));
            }
        }
    }

    private static List<DayPoint> daily(List<CurrencyMovement> window, Instant from, Instant to, List<Anomaly> anomalies) {
        LocalDate first = dayOf(from);
        LocalDate last = dayOf(to);
        TreeMap<LocalDate, Map<CurrencySource, long[]>> byDay = new TreeMap<>();
        TreeMap<LocalDate, long[]> eventPointsByDay = new TreeMap<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            byDay.put(day, new EnumMap<>(CurrencySource.class));
            eventPointsByDay.put(day, new long[2]);
        }
        for (CurrencyMovement movement : window) {
            LocalDate day = dayOf(movement.getOccurredAt());
            Map<CurrencySource, long[]> flows = byDay.get(day);
            if (flows == null) {
                continue;
            }
            long[] flow = flows.computeIfAbsent(movement.getSource(), ignored -> new long[2]);
            flow[0] += Math.max(0, movement.getTokenDelta());
            flow[1] += Math.max(0, -movement.getTokenDelta());
            long[] eventPoints = eventPointsByDay.get(day);
            eventPoints[0] += Math.max(0, movement.getEventPointsDelta());
            eventPoints[1] += Math.max(0, -movement.getEventPointsDelta());
        }

        List<DayPoint> points = new ArrayList<>();
        Map<CurrencySource, List<Long>> inflowSeries = new EnumMap<>(CurrencySource.class);
        List<LocalDate> dayOrder = new ArrayList<>(byDay.keySet());
        byDay.forEach((day, flows) -> {
            Map<String, Long> inflow = new LinkedHashMap<>();
            Map<String, Long> outflow = new LinkedHashMap<>();
            for (CurrencySource source : CurrencySource.values()) {
                long[] flow = flows.getOrDefault(source, new long[2]);
                inflow.put(source.name(), flow[0]);
                outflow.put(source.name(), flow[1]);
                inflowSeries.computeIfAbsent(source, ignored -> new ArrayList<>()).add(flow[0]);
            }
            long[] eventPoints = eventPointsByDay.get(day);
            points.add(new DayPoint(day.toString(), inflow, outflow, eventPoints[0], eventPoints[1]));
        });

        // Un jour qui sort de trois ecarts-types de ceux qui le precedent. Il faut une
        // semaine d'historique pour qu'un ecart-type veuille dire quelque chose.
        inflowSeries.forEach((source, values) -> {
            for (int index = 7; index < values.size(); index++) {
                List<Long> previous = values.subList(0, index);
                double mean = previous.stream().mapToLong(Long::longValue).average().orElse(0);
                double variance = previous.stream().mapToDouble(value -> (value - mean) * (value - mean)).average().orElse(0);
                double threshold = mean + 3 * Math.sqrt(variance);
                long value = values.get(index);
                if (value >= ANOMALY_FLOOR && value > threshold && value > 2 * mean) {
                    anomalies.add(new Anomaly("SOURCE_DAY_SPIKE", "WARNING",
                            "Pic de gains sur une journée",
                            source.name() + " a distribué " + value + " tokens ce jour-là, contre " + Math.round(mean)
                                    + " en moyenne les jours précédents.",
                            null, null, source.name(), dayOrder.get(index).toString(), value, Math.round(threshold)));
                }
            }
        });
        return points;
    }

    private static List<Earner> topEarners(List<CurrencyMovement> window, long totalInflow,
                                           Map<UUID, AppUser> playersById, Map<UUID, Department> departmentsById) {
        Map<UUID, Map<CurrencySource, Long>> gains = new HashMap<>();
        for (CurrencyMovement movement : window) {
            if (movement.getTokenDelta() > 0) {
                gains.computeIfAbsent(movement.getUserId(), ignored -> new EnumMap<>(CurrencySource.class))
                        .merge(movement.getSource(), movement.getTokenDelta(), Long::sum);
            }
        }
        return gains.entrySet().stream()
                .map(entry -> {
                    long total = entry.getValue().values().stream().mapToLong(Long::longValue).sum();
                    AppUser player = playersById.get(entry.getKey());
                    Map<String, Long> bySource = new LinkedHashMap<>();
                    entry.getValue().forEach((source, amount) -> bySource.put(source.name(), amount));
                    return new Earner(entry.getKey(), nameOf(playersById, entry.getKey()),
                            departmentName(player, departmentsById), total, bySource,
                            totalInflow == 0 ? 0 : (double) total / totalInflow,
                            player == null ? 0 : player.getTokens(), player == null ? 0 : player.getEventPoints());
                })
                .sorted(Comparator.comparingLong(Earner::tokensIn).reversed())
                .limit(15)
                .toList();
    }

    private static List<DepartmentStats> departments(List<CurrencyMovement> window, List<AppUser> players,
                                                     Map<UUID, Department> departmentsById) {
        Map<UUID, UUID> departmentOfPlayer = new HashMap<>();
        Map<UUID, Integer> headcount = new HashMap<>();
        for (AppUser player : players) {
            departmentOfPlayer.put(player.getId(), player.getDepartment());
            if (player.getDepartment() != null) {
                headcount.merge(player.getDepartment(), 1, Integer::sum);
            }
        }
        Map<UUID, Map<CurrencySource, Long>> gains = new HashMap<>();
        for (CurrencyMovement movement : window) {
            UUID department = departmentOfPlayer.get(movement.getUserId());
            if (department != null && movement.getTokenDelta() > 0) {
                gains.computeIfAbsent(department, ignored -> new EnumMap<>(CurrencySource.class))
                        .merge(movement.getSource(), movement.getTokenDelta(), Long::sum);
            }
        }
        // Rapporte a l'effectif : un grand departement gagne forcement plus au total,
        // et c'est le gain par tete qui dit si l'economie avantage l'un d'eux.
        return departmentsById.values().stream()
                .map(department -> {
                    Map<CurrencySource, Long> bySource = gains.getOrDefault(department.getId(), Map.of());
                    long total = bySource.values().stream().mapToLong(Long::longValue).sum();
                    int count = headcount.getOrDefault(department.getId(), 0);
                    Map<String, Long> named = new LinkedHashMap<>();
                    bySource.forEach((source, amount) -> named.put(source.name(), amount));
                    return new DepartmentStats(department.getShortName(), department.getColor(), count, total,
                            count == 0 ? 0 : (double) total / count, named);
                })
                .sorted(Comparator.comparingDouble(DepartmentStats::tokensPerPlayer).reversed())
                .toList();
    }

    // ── Soldes ──────────────────────────────────────────────────────────────────

    private static Balances balances(List<AppUser> players, List<Anomaly> anomalies) {
        long[] tokens = players.stream().mapToLong(player -> player.getTokens() == null ? 0 : player.getTokens()).toArray();
        long[] eventPoints = players.stream().mapToLong(AppUser::getEventPoints).toArray();
        long tokenSupply = Arrays.stream(tokens).filter(value -> value > 0).sum();
        long eventPointSupply = Arrays.stream(eventPoints).sum();

        for (AppUser player : players) {
            if (player.getTokens() != null && player.getTokens() < 0) {
                anomalies.add(new Anomaly("NEGATIVE_BALANCE", "CRITICAL", "Solde négatif",
                        player.getDisplayName() + " a un solde de " + player.getTokens()
                                + " tokens. Aucune règle ne le permet : une écriture a contourné les gardes.",
                        player.getId(), player.getDisplayName(), null, null, player.getTokens(), 0));
            }
        }

        long[] descending = Arrays.stream(tokens).map(value -> Math.max(0, value)).sorted().toArray();
        reverse(descending);
        double top1 = shareOfTop(descending, tokenSupply, 0.01);
        double top10 = shareOfTop(descending, tokenSupply, 0.10);
        if (players.size() >= 50 && top1 > CONCENTRATION_SHARE) {
            anomalies.add(new Anomaly("TOKEN_CONCENTRATION", "WARNING", "Tokens très concentrés",
                    "Le 1 % des joueurs les plus riches détient " + Math.round(top1 * 100)
                            + " % des tokens en circulation. Une source rapporte peut-être trop à quelques-uns.",
                    null, null, null, null, Math.round(top1 * 100), Math.round(CONCENTRATION_SHARE * 100)));
        }

        long[] ascending = descending.clone();
        Arrays.sort(ascending);
        return new Balances(tokenSupply, eventPointSupply, players.size(),
                ascending.length == 0 ? 0 : ascending[ascending.length / 2],
                top1, top10,
                (int) Arrays.stream(tokens).filter(value -> value <= 0).count(),
                histogram(tokens), histogram(eventPoints));
    }

    // ── Historique exact : defis et parties ─────────────────────────────────────

    /**
     * Ce que chaque defi a reellement distribue, depuis toujours.
     *
     * <p>Les regles sont celles du recalcul des points : le quota compte dans
     * l'ordre de validation, un defi de promo ne rapporte rien a ses membres, et
     * une recompense partagee se divise au lieu de se multiplier.</p>
     */
    private List<ChallengePayout> challengePayouts() {
        Map<UUID, Integer> rank = new HashMap<>();
        Map<UUID, long[]> counters = new LinkedHashMap<>();
        Map<UUID, Challenge> challenges = new HashMap<>();

        for (ChallengeSubmission submission : submissionRepository.findValidatedSubmissionsForPointRecalculation()) {
            Challenge challenge = submission.getChallenge();
            if (challenge == null || challenge.getId() == null) {
                continue;
            }
            challenges.putIfAbsent(challenge.getId(), challenge);
            long[] counter = counters.computeIfAbsent(challenge.getId(), ignored -> new long[3]);
            counter[0]++;

            int rewardRank = rank.merge(challenge.getId(), 1, Integer::sum);
            Integer quota = challenge.getGlobalQuota();
            boolean withinQuota = quota == null || quota == 0 || rewardRank <= quota;
            if (!withinQuota || challenge.isPromo()) {
                continue;
            }
            counter[1]++;
            long reward = (challenge.getPointsReward() == null ? 0 : challenge.getPointsReward())
                    + (submission.getBonusPoints() == null ? 0 : submission.getBonusPoints());
            int members = 1 + submission.getParticipants().size();
            counter[2] += challenge.isRewardShared() ? reward : reward * members;
        }

        return counters.entrySet().stream()
                .map(entry -> {
                    Challenge challenge = challenges.get(entry.getKey());
                    long[] counter = entry.getValue();
                    return new ChallengePayout(challenge.getId(), challenge.getTitle(), challenge.getCategory(),
                            challenge.getPointsReward() == null ? 0 : challenge.getPointsReward(),
                            (int) counter[0], (int) counter[1], counter[2], challenge.isPromo());
                })
                .sorted(Comparator.comparingLong(ChallengePayout::tokensDistributed).reversed())
                .limit(30)
                .toList();
    }

    /** Ce que rapporte une partie, depuis toujours : les jetons de retraite sont gardes run par run. */
    private GameRuns gameRuns(Map<UUID, AppUser> playersById, List<Anomaly> anomalies) {
        List<Object[]> rows = runRepository.findRetirementPayouts();
        List<Long> payouts = new ArrayList<>();
        long total = 0;
        for (Object[] row : rows) {
            long tokens = ((Number) row[0]).longValue();
            payouts.add(tokens);
            total += tokens;
        }

        if (payouts.size() >= 20) {
            long median = percentile(sorted(payouts), 50);
            long threshold = Math.max(200, median * OUTLIER_FACTOR);
            rows.stream()
                    .filter(row -> ((Number) row[0]).longValue() > threshold)
                    .sorted(Comparator.comparingLong((Object[] row) -> ((Number) row[0]).longValue()).reversed())
                    .limit(5)
                    .forEach(row -> {
                        long tokens = ((Number) row[0]).longValue();
                        UUID owner = (UUID) row[2];
                        Instant endedAt = (Instant) row[1];
                        anomalies.add(new Anomaly("GAME_RUN_OUTLIER", "WARNING", "Partie hors norme",
                                "Une partie de " + nameOf(playersById, owner) + " a rapporté " + tokens
                                        + " tokens, plus de " + OUTLIER_FACTOR + " fois une partie typique (" + median + ").",
                                owner, nameOf(playersById, owner), CurrencySource.GAME_RUN.name(),
                                endedAt == null ? null : dayOf(endedAt).toString(), tokens, threshold));
                    });
        }

        long[] values = payouts.stream().mapToLong(Long::longValue).toArray();
        return new GameRuns(payouts.size(), total, distribution(payouts), histogram(values));
    }

    // ── Outils ──────────────────────────────────────────────────────────────────

    static Distribution distribution(List<Long> values) {
        if (values.isEmpty()) {
            return new Distribution(0, 0, 0, 0, 0, 0);
        }
        long[] sorted = sorted(values);
        double mean = Arrays.stream(sorted).average().orElse(0);
        return new Distribution(sorted.length, Math.round(mean * 10) / 10.0,
                percentile(sorted, 50), percentile(sorted, 90), percentile(sorted, 99), sorted[sorted.length - 1]);
    }

    private static long[] sorted(List<Long> values) {
        long[] array = values.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(array);
        return array;
    }

    /** Rang le plus proche : simple, et ne fabrique jamais une valeur qui n'existe pas. */
    static long percentile(long[] sorted, double percentile) {
        if (sorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static List<Bucket> histogram(long[] values) {
        int[] counts = new int[BUCKET_EDGES.length];
        for (long value : values) {
            int bucket = 0;
            for (int index = 0; index < BUCKET_EDGES.length; index++) {
                if (Math.max(0, value) >= BUCKET_EDGES[index]) {
                    bucket = index;
                }
            }
            counts[bucket]++;
        }
        List<Bucket> buckets = new ArrayList<>();
        for (int index = 0; index < BUCKET_EDGES.length; index++) {
            Long upper = index + 1 < BUCKET_EDGES.length ? BUCKET_EDGES[index + 1] - 1 : null;
            buckets.add(new Bucket(BUCKET_EDGES[index], upper, counts[index]));
        }
        return buckets;
    }

    private static double shareOfTop(long[] descending, long supply, double fraction) {
        if (descending.length == 0 || supply == 0) {
            return 0;
        }
        int count = Math.max(1, (int) Math.ceil(descending.length * fraction));
        long held = 0;
        for (int index = 0; index < count; index++) {
            held += descending[index];
        }
        return (double) held / supply;
    }

    private static void reverse(long[] values) {
        for (int left = 0, right = values.length - 1; left < right; left++, right--) {
            long swap = values[left];
            values[left] = values[right];
            values[right] = swap;
        }
    }

    private static LocalDate dayOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    private static String nameOf(Map<UUID, AppUser> playersById, UUID userId) {
        AppUser player = playersById.get(userId);
        return player == null ? "Joueur supprimé" : player.getDisplayName();
    }

    private static String departmentName(AppUser player, Map<UUID, Department> departmentsById) {
        if (player == null || player.getDepartment() == null) {
            return null;
        }
        Department department = departmentsById.get(player.getDepartment());
        return department == null ? null : department.getShortName();
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }
}
