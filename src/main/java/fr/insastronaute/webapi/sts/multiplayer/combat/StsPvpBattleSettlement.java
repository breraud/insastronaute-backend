package fr.insastronaute.webapi.sts.multiplayer.combat;

import fr.insastronaute.webapi.sts.combat.event.CombatEnded;
import fr.insastronaute.webapi.sts.combat.event.CombatEvent;
import fr.insastronaute.webapi.sts.combat.model.CombatProgress;
import fr.insastronaute.webapi.sts.combat.model.CombatState;
import fr.insastronaute.webapi.sts.combat.model.CombatStatus;
import fr.insastronaute.webapi.sts.combat.model.CombatantState;
import fr.insastronaute.webapi.sts.multiplayer.domain.StsPvpProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Decides when a PvP battle is over and moves the players' rating.
 *
 * <p>Le classement n'est plus plat : ce qu'un résultat vaut dépend de qui était en face, et le
 * calcul vit dans {@link StsPvpRating}. Ce qui reste ici est de savoir <em>qui</em> a gagné, et à
 * qui appliquer quoi.
 */
public final class StsPvpBattleSettlement {

    private StsPvpBattleSettlement() {
    }

    /**
     * The team id convention shared with the PvP combat setup: team index {@code i} is
     * {@code "team-i"}, matching {@code StsPvpBattle.winnerTeam} and the {@code teams} array.
     */

    /**
     * The index of the one team still standing, or empty when that is not what happened.
     *
     * <p>Empty covers two different things on purpose, because they call for the same answer
     * here: a combat still running with several living teams, and a draw that wiped out everyone
     * and left no winner at all.
     *
     * <p>The search is over the teams the state actually names rather than over {@code 0} and
     * {@code 1}. That was the same thing while a battle was always one player against one, and it
     * silently stops being so the moment a mode has more.
     */
    public static OptionalInt winningTeamIndex(CombatState state) {
        Objects.requireNonNull(state, "state");
        Set<String> livingTeams = state.combatants().stream()
                .filter(combatant -> combatant.hp() > 0)
                .map(CombatantState::teamId)
                .collect(Collectors.toSet());

        if (livingTeams.size() != 1) {
            return OptionalInt.empty();
        }

        String survivingTeam = livingTeams.iterator().next();
        Set<String> allTeams = state.combatants().stream()
                .map(CombatantState::teamId)
                .collect(Collectors.toSet());
        for (int teamIndex = 0; teamIndex < allTeams.size(); teamIndex++) {
            if (StsPvpCombatSetupFactory.teamId(teamIndex).equals(survivingTeam)) {
                return OptionalInt.of(teamIndex);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * A conceded combat and what it has to announce.
     *
     * @param applied  whether the concession changed anything at all. False when there was
     *                 nothing left to concede, and it is a separate field rather than an empty
     *                 event list because a concession can legitimately have nothing to announce:
     *                 in a team mode, a player who gives up while their team fights on has
     *                 conceded for real and the combat simply carries on without them.
     * @param finished whether that concession ended the combat. It does not in a team mode until
     *                 the last member of a team is out, which is the whole point of having one.
     */
    public record Conceded(
            CombatState state, List<CombatEvent> events, boolean applied, boolean finished) {

        public Conceded {
            Objects.requireNonNull(state, "state");
            events = List.copyOf(Objects.requireNonNull(events, "events"));
        }

        /** Nothing to give up: the combat was already over. */
        static Conceded nothingToConcede(CombatState state) {
            return new Conceded(state, List.of(), false, false);
        }
    }

    /**
     * The combat as it stands once {@code forfeitingCombatantId} has conceded it, and what that
     * has to be announced with.
     *
     * <p>A forfeit is not something the engine can express: there is no command for giving up, and
     * a battle that ends because one player stopped answering ends outside the rules of combat.
     * What is written here is therefore the outcome and never a resolution — the deserter is put
     * at 0 hp, the survivor's team becomes the winner — and the engine, seeing a finished combat,
     * refuses every later command on its own. Leaving the state untouched instead would leave both
     * clients showing a combat still under way and the deserter free to come back and play it.
     *
     * @return the conceded combat and its {@link CombatEnded}, or {@code state} itself and nothing
     *         to say when it was already over
     */
    public static Conceded conceded(CombatState state, String forfeitingCombatantId) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(forfeitingCombatantId, "forfeitingCombatantId");
        if (state.status() != CombatStatus.ACTIVE) {
            return Conceded.nothingToConcede(state);
        }

        List<CombatantState> combatants = state.combatants().stream()
                .map(combatant -> combatant.combatantId().equals(forfeitingCombatantId)
                        ? withoutHp(combatant)
                        : combatant)
                .toList();

        // The winner rule lives in CombatProgress and is asked for here rather than written
        // again. It was written again, and a rule with two authors drifts: this copy answered
        // for forfeits only, and nothing made the two agree.
        CombatProgress progress = CombatProgress.of(combatants, state.timeline());
        long resultingRevision = state.revision() + 1;

        if (!progress.finished()) {
            // Une equipe tient encore : celui qui abandonne quitte le combat, le combat continue.
            // Le forcer a FINISHED ici mettait fin a la partie de son equipier aussi, ce qui
            // etait sans consequence tant qu'il n'y avait qu'un joueur par equipe.
            //
            // Le tour doit changer de main quand c'etait celui du partant : sans cela l'etat
            // designerait un combattant mort comme actif, ce que CombatState refuse de construire.
            CombatState carriesOn = new CombatState(
                    state.combatId(),
                    resultingRevision,
                    state.dataVersion(),
                    CombatStatus.ACTIVE,
                    null,
                    state.random(),
                    combatants,
                    progress.timeline(),
                    progress.nextActiveCombatantId(null, combatants),
                    state.counters());
            // Rien a annoncer : l'etat porte les 0 pv du partant, et le client lit les points de
            // vie de l'etat. Il n'y a pas de fin de combat a raconter.
            return new Conceded(carriesOn, List.of(), true, false);
        }

        CombatState finished = new CombatState(
                state.combatId(),
                resultingRevision,
                state.dataVersion(),
                CombatStatus.FINISHED,
                progress.winnerTeamId(),
                state.random(),
                combatants,
                state.timeline(),
                null,
                state.counters());

        // Announced, because the client reads the outcome from the event and not from the
        // state: a combat that ends without saying so leaves it showing a fight still going.
        return new Conceded(finished, List.of(new CombatEnded(
                state.combatId(), resultingRevision, progress.winnerTeamId())), true, true);
    }

    private static CombatantState withoutHp(CombatantState combatant) {
        return new CombatantState(
                combatant.combatantId(),
                combatant.teamId(),
                combatant.controllerType(),
                combatant.maxHp(),
                0,
                combatant.armor(),
                combatant.energy(),
                combatant.baseEnergy(),
                combatant.turnDelayTicks(),
                combatant.piles(),
                combatant.statuses(),
                combatant.relics(),
                combatant.intentCardId(),
                combatant.patternIndex(),
                combatant.relicMemory());
    }

    /**
     * Un joueur et le profil qui porte son classement.
     *
     * <p>L'identifiant vient du siège plutôt que du profil : le profil ne l'atteint qu'en
     * chargeant l'utilisateur associé, et une association paresseuse n'est pas quelque chose que
     * ce calcul doit déclencher.
     */
    public record RatedPlayer(UUID userId, StsPvpProfile profile) {

        public RatedPlayer {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(profile, "profile");
        }

        int elo(StsPvpRatingRules rules) {
            Integer elo = profile.getElo();
            return elo == null ? rules.defaultElo() : elo;
        }
    }

    /**
     * Règle le duel pour tout le monde : compteurs, classement, et ce que ça vaut en jetons.
     *
     * <p><b>Ce qu'un résultat rapporte dépend de qui était en face</b>, et c'est le changement de
     * fond par rapport au barème plat de ±25 qui précédait : celui-ci payait autant l'exploit que
     * la victoire acquise d'avance, et se cultivait en ne défiant que plus faible que soi. Le
     * calcul lui-même vit dans {@link StsPvpRating} ; ce qui se décide ici est à qui l'appliquer.
     *
     * <p>Une équipe est jugée sur la moyenne de ses classements, si bien qu'un 2v2 se règle comme
     * un duel entre deux joueurs de cette force-là.
     *
     * <p><b>Un duel amical ne rapporte rien et ne coûte rien</b>, ni classement ni jetons : il ne
     * bouge que les compteurs amicaux. Deux joueurs peuvent en arranger autant qu'ils veulent, et
     * tout gain s'y récolterait à volonté.
     *
     * <p>Les deux camps doivent avoir des joueurs. Ce n'est pas une formalité : un raid se joue
     * contre un boss que le serveur pilote, et il n'y a personne en face à qui prendre des points.
     *
     * @return ce que chacun a gagné ou perdu, à ranger sur la bataille et à montrer au joueur.
     *         Le portefeuille, lui, n'est pas touché ici — voir {@link Entry#tokenDelta()}.
     */
    public static List<StsPvpBattleRewards.Entry> applyTeamRating(
            List<RatedPlayer> winners, List<RatedPlayer> losers, boolean friendly) {
        return applyTeamRating(winners, losers, friendly, StsPvpRatingRules.DEFAULT);
    }

    /**
     * @param rules le barème à appliquer, tel que {@code gameplay/pvp_rating.json} le pose
     */
    public static List<StsPvpBattleRewards.Entry> applyTeamRating(
            List<RatedPlayer> winners,
            List<RatedPlayer> losers,
            boolean friendly,
            StsPvpRatingRules rules) {
        Objects.requireNonNull(winners, "winners");
        Objects.requireNonNull(losers, "losers");
        Objects.requireNonNull(rules, "rules");
        if (winners.isEmpty() || losers.isEmpty()) {
            return List.of();
        }

        if (friendly) {
            winners.forEach(winner -> winner.profile()
                    .setWinsFriendly(winner.profile().getWinsFriendly() + 1));
            losers.forEach(loser -> loser.profile()
                    .setLossesFriendly(loser.profile().getLossesFriendly() + 1));
            return List.of();
        }

        // La force de chaque camp est lue avant que quoi que ce soit ne bouge : sinon le premier
        // gagnant réglé changerait la moyenne sur laquelle le second est évalué, et l'ordre de la
        // liste déciderait des gains.
        int winnerTeamRating = StsPvpRating.teamRating(
                rules, winners.stream().map(winner -> winner.elo(rules)).toList());
        int loserTeamRating = StsPvpRating.teamRating(
                rules, losers.stream().map(loser -> loser.elo(rules)).toList());

        List<StsPvpBattleRewards.Entry> rewards = new ArrayList<>();
        for (RatedPlayer winner : winners) {
            rewards.add(settle(rules, winner, loserTeamRating, true));
        }
        for (RatedPlayer loser : losers) {
            rewards.add(settle(rules, loser, winnerTeamRating, false));
        }
        return List.copyOf(rewards);
    }

    private static StsPvpBattleRewards.Entry settle(
            StsPvpRatingRules rules, RatedPlayer player, int opponentTeamRating, boolean won) {
        StsPvpProfile profile = player.profile();
        int before = player.elo(rules);
        int after = StsPvpRating.ratingAfter(rules, before, opponentTeamRating, won);

        profile.setElo(after);
        if (won) {
            profile.setWinsRanked(profile.getWinsRanked() + 1);
        } else {
            profile.setLossesRanked(profile.getLossesRanked() + 1);
        }

        return new StsPvpBattleRewards.Entry(
                player.userId(),
                won,
                before,
                after,
                after - before,
                StsPvpRating.tokenDelta(rules, before, opponentTeamRating, won));
    }
}
