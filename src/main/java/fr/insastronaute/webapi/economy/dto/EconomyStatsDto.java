package fr.insastronaute.webapi.economy.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que l'onglet stats montre de l'economie des tokens et des points Events.
 *
 * <p>Deux natures de chiffres, et il faut les distinguer. Les flux — sources,
 * jours, meilleurs gains, departements, anomalies — viennent du registre des
 * mouvements, qui n'existe que depuis son deploiement. Les soldes, les gains par
 * defi et par partie viennent des tables du jeu et couvrent tout l'historique.</p>
 */
public record EconomyStatsDto(
        Instant from,
        Instant to,
        int days,
        Totals totals,
        List<SourceStats> sources,
        List<DayPoint> daily,
        List<Earner> topEarners,
        List<DepartmentStats> departments,
        Balances balances,
        List<ChallengePayout> challengePayouts,
        GameRuns gameRuns,
        List<Anomaly> anomalies
) {

    public record Totals(long tokensIn, long tokensOut, long eventPointsIn, long eventPointsOut,
                         int activePlayers, long movements) {}

    /**
     * Une distribution resumee.
     *
     * <p>La mediane et les centiles plutot que la seule moyenne : un joueur qui
     * gagne mille fois plus que les autres deplace la moyenne sans rien dire du
     * joueur typique, et c'est justement lui qu'on veut voir.</p>
     */
    public record Distribution(long count, double mean, long median, long p90, long p99, long max) {}

    public record SourceStats(
            String source,
            long tokensIn,
            long tokensOut,
            long eventPointsIn,
            long eventPointsOut,
            long movements,
            int players,
            /** Part de cette source dans tous les tokens gagnes sur la fenetre, de 0 a 1. */
            double shareOfInflow,
            /** Ce que rapporte — ou coute — une action de cette source. */
            Distribution perAction,
            /** Ce qu'un joueur actif en a tire sur la fenetre. */
            Distribution perPlayer
    ) {}

    /** Un jour, heure de Paris. Toutes les sources y figurent, meme a zero : les graphiques empilent. */
    public record DayPoint(String day, Map<String, Long> inflow, Map<String, Long> outflow,
                           long eventPointsIn, long eventPointsOut) {}

    public record Earner(UUID userId, String displayName, String department, long tokensIn,
                         Map<String, Long> bySource, double shareOfInflow,
                         long tokenBalance, long eventPointBalance) {}

    public record DepartmentStats(String department, String color, int players, long tokensIn,
                                  double tokensPerPlayer, Map<String, Long> bySource) {}

    /** Une tranche d'histogramme. {@code to} vaut null pour la derniere, ouverte. */
    public record Bucket(long from, Long to, int players) {}

    public record Balances(long tokenSupply, long eventPointSupply, int players, long medianTokens,
                           double top1PercentShare, double top10PercentShare, int playersAtZero,
                           List<Bucket> tokenHistogram, List<Bucket> eventPointHistogram) {}

    public record ChallengePayout(UUID challengeId, String title, String category, long pointsPerValidation,
                                  int validations, int rewardedValidations, long tokensDistributed,
                                  boolean promo) {}

    public record GameRuns(long runs, long tokensDistributed, Distribution perRun, List<Bucket> histogram) {}

    public record Anomaly(String type, String severity, String title, String detail, UUID userId,
                          String displayName, String source, String day, long value, long threshold) {}
}
