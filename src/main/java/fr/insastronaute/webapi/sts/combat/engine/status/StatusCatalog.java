package fr.insastronaute.webapi.sts.combat.engine.status;

import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardTag;
import fr.insastronaute.webapi.sts.combat.model.StatusInstance;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The one place that says what a status is.
 *
 * <p>Whether a status counts as a buff, whether a card may strip it, whether its duration can be
 * extended and how much of it a combatant may hold were previously answered by short hardcoded
 * lists scattered across the dispel, steal and transfer handlers. Those lists disagreed with each
 * other and with the data: {@code Fragile} was spelled {@code Frail} and therefore never matched
 * anything the game actually applies.
 *
 * <p>The table below mirrors Unity's status classes, including the ones whose category depends on
 * the sign of their value.
 */
public final class StatusCatalog {

    /** Follow-ups Unity's own constructor treats as permanent unless authored a positive duration. */
    private static final java.util.Set<String> COUNTS_CARDS_NOT_TURNS =
            java.util.Set.of("CardFollowUp", "AnyCardFollowUp", "FieldTurnFollowUp");

    /** Durations Unity's own constructor sets outright, whatever the data authored. */
        private static final Map<String, Integer> FIXED_DURATION =
            Map.of("After", 1, "Confusion", 1, "Propulsion", 1);

    private static final Map<String, StatusDefinition> BY_ID = index(
            entry("Absorption", "Absorption", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Accelerate", "Accélération", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Analyse", "Analyse", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Adaptation", "Adaptation", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("After", "After", StatusCategory.DEBUFF, StatusShape.VALUE, false, false, true, 99),
            entry("AnyCardFollowUp", "AnyCardFollowUp", StatusCategory.NEUTRAL, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            entry("AreaDmgReduction", "Réduction de dégâts de zone", StatusCategory.BUFF, StatusShape.VALUE_AND_DURATION, true, false, false, 99),
            entry("ArmorBlock", "Anti-armure", StatusCategory.DEBUFF, StatusShape.DURATION, true, true, true, 99),
            entry("Artifact", "Artefact", StatusCategory.SIGNED, StatusShape.VALUE, false, false, false, 99),
            // Stellaire : la carte repart quatre fois, mais chacun de ses effets ne vaut plus 1.
            entry("Astra", "Stellaire", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Awakening", "Éveil", StatusCategory.BUFF, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            // La durée d'Aveuglé compte des coups annulés, pas des tours : voir StatusBehaviours.
            entry("Blind", "Aveuglé", StatusCategory.DEBUFF, StatusShape.DURATION, false, false, true, 99),
            entry("Brand", "Marque", StatusCategory.DEBUFF, StatusShape.VALUE_AND_DURATION, true, false, false, 99),
            entry("Burn", "Brûlure", StatusCategory.DEBUFF, StatusShape.DURATION, false, false, false, 99),
            entry("CardFollowUp", "CardFollowUp", StatusCategory.NEUTRAL, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            entry("Clock", "Horloge", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            // Chaos : ne porte ni valeur ni duree, il change ce que son porteur fait.
            entry("Chaotic", "Chaos", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Confusion", "Confusion", StatusCategory.DEBUFF, StatusShape.FIXED, true, true, true, 99),
            entry("ConserveArmor", "Conservation d'armure", StatusCategory.BUFF, StatusShape.VALUE_AND_DURATION, true, false, false, 99),
            entry("Compilation", "Compilation", StatusCategory.DEBUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Continuous", "Milieu continu", StatusCategory.DEBUFF, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            entry("CostNullify", "Coût nul", StatusCategory.BUFF, StatusShape.VALUE, false, false, false, 99),
            // Critique : les {valeur} prochaines attaques du porteur frappent deux fois plus fort.
            entry("Critical", "Critique", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Crystallize", "Cristallisation", StatusCategory.DEBUFF, StatusShape.DURATION, false, false, false, 3),
            entry("DamageReduction", "Réduction de dégâts", StatusCategory.BUFF, StatusShape.VALUE_AND_DURATION, true, false, false, 99),
            entry("DebuffHalt", "Isolement thermique", StatusCategory.DEBUFF, StatusShape.DURATION, true, false, false, 99),
            entry("DelayImmunity", "Anti-délai", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("DispelBlock", "Brouillage", StatusCategory.DEBUFF, StatusShape.DURATION, true, false, false, 99),
            entry("DelayedDeath", "Mort retardée", StatusCategory.DEBUFF, StatusShape.DURATION, true, true, false, 99),
            entry("DelayedStun", "Étourdissement retardé", StatusCategory.DEBUFF, StatusShape.DURATION, false, false, false, 99),
            entry("Dexterity", "Dextérité", StatusCategory.SIGNED, StatusShape.VALUE, false, false, false, 99),
            entry("Divination", "Divination", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Echo", "Écho", StatusCategory.BUFF, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            entry("Elastic", "Élasticité", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Energy", "Énergie", StatusCategory.SIGNED, StatusShape.VALUE, true, false, false, 99),
            entry("EnergyUp", "Énergie+", StatusCategory.BUFF, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            entry("Fatigue", "Fatigue", StatusCategory.DEBUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Fervor", "Ferveur", StatusCategory.BUFF, StatusShape.DURATION, true, false, false, 99),
            entry("FieldTurnFollowUp", "FieldTurnFollowUp", StatusCategory.NEUTRAL, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            entry("Filter", "Filtre", StatusCategory.SIGNED, StatusShape.VALUE, false, false, false, 99),
            entry("Fragile", "Fragilité", StatusCategory.DEBUFF, StatusShape.DURATION, false, false, false, 99),
            entry("Freeze", "Congélation", StatusCategory.DEBUFF, StatusShape.DURATION, true, false, false, 99),
            entry("FullBreak", "Déchéance", StatusCategory.DEBUFF, StatusShape.DURATION, true, false, false, 99),
            entry("FullMoon", "Pleine lune", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("FullMoonTemp", "Perte de Force", StatusCategory.NEUTRAL, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            entry("FullThrottle", "Plein régime", StatusCategory.BUFF, StatusShape.VALUE, false, false, true, 99),
            entry("Haste", "Célérité", StatusCategory.BUFF, StatusShape.DURATION, false, false, false, 99),
            entry("Hide", "Cachette", StatusCategory.BUFF, StatusShape.VALUE_AND_DURATION, true, false, false, 99),
            entry("Imitate", "Imitation", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Infaillible", "Infaillible", StatusCategory.BUFF, StatusShape.VALUE, false, false, false, 99),
            entry("Jump", "Saut", StatusCategory.BUFF, StatusShape.VALUE, true, true, false, 99),
            entry("LifeCycle", "Cycle de vie", StatusCategory.BUFF, StatusShape.VALUE, false, false, true, 99),
            entry("Link", "Lien", StatusCategory.NEUTRAL, StatusShape.FIXED, false, false, false, 99),
            // Lunaire : les attaques passent outre l'armure et les reductions de la cible.
            entry("Luna", "Lunaire", StatusCategory.BUFF, StatusShape.DURATION, true, false, false, 99),
            entry("MechaArm", "Bras mécatronique", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Momentum", "Élan", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Overpower", "Domination", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Plating", "Blindage", StatusCategory.BUFF, StatusShape.DURATION, false, false, false, 99),
            entry("Poison", "Poison", StatusCategory.DEBUFF, StatusShape.DURATION, false, false, false, 99),
            entry("PowerPassive", "Pouvoir", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Production", "Production", StatusCategory.SIGNED, StatusShape.VALUE, true, false, false, 99),
            entry("Provocation", "Provocation", StatusCategory.DEBUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Rage", "Rage", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Charge", "Charge", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 3),
            entry("Regen", "Régénération", StatusCategory.BUFF, StatusShape.DURATION, false, false, false, 99),
            entry("Regulator", "Régulateur", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("RiskManagement", "Gestion du risque", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Sadism", "Préparation...", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            // Les deux stades suivants de Sadisme : ni l'un ni l'autre ne se pose, ils naissent
            // du précédent quand un adversaire faiblit (StatusEvolution).
            entry("SadismII", "Préparation...", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("SadismIII", "Prêt à annihiler", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Sap", "Sape", StatusCategory.DEBUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Shielding", "Garde", StatusCategory.BUFF, StatusShape.DURATION, false, false, false, 99),
            // Signal vient d'une carte de Puissance : FIXED lui donne une durée infinie.
            entry("Signal", "Signal", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Slow", "Lenteur", StatusCategory.DEBUFF, StatusShape.DURATION, false, false, false, 99),
            entry("Smoke", "Fumigène", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            // Solaire : la moitie des degats portes revient en soin.
            entry("Sol", "Solaire", StatusCategory.BUFF, StatusShape.DURATION, true, false, false, 99),
            entry("Speed", "Vitesse", StatusCategory.SIGNED, StatusShape.VALUE, false, false, false, 99),
            entry("Status", "Statuts", StatusCategory.DEBUFF, StatusShape.DURATION, true, false, false, 0),
            entry("Strength", "Force", StatusCategory.SIGNED, StatusShape.VALUE, false, false, true, 99),
            entry("Strengthen", "Renforcement", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Stun", "Étourdissement", StatusCategory.DEBUFF, StatusShape.DURATION, true, true, true, 99),
            entry("Targeting", "Ciblage", StatusCategory.NEUTRAL, StatusShape.FIXED, true, false, false, 99),
            entry("Thorns", "Épines", StatusCategory.BUFF, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            entry("Steadfast", "Ancrage", StatusCategory.BUFF, StatusShape.DURATION, true, false, false, 99),
            entry("TimeCompression", "Compression temporelle", StatusCategory.BUFF, StatusShape.DURATION, true, true, false, 99),
            // Distorsion temporelle : chaque carte jouee repousse le tour de quelqu'un.
            entry("TimeDistortion", "Distorsion temporelle", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("TimeStop", "Arrêt du temps", StatusCategory.DEBUFF, StatusShape.DURATION, true, false, false, 99),
            entry("Exposure", "Exposition", StatusCategory.DEBUFF, StatusShape.VALUE_AND_DURATION, false, false, false, 99),
            entry("Trap", "Piège", StatusCategory.DEBUFF, StatusShape.VALUE_AND_DURATION, false, false, true, 99),
            entry("Unbiased", "Impartial", StatusCategory.BUFF, StatusShape.DURATION, true, false, false, 99),
            entry("Undead", "Mort-vivant", StatusCategory.DEBUFF, StatusShape.DURATION, true, false, false, 99),
            entry("Vigor", "Vigueur", StatusCategory.BUFF, StatusShape.VALUE, false, false, false, 99),
            entry("Vuln", "Vulnérable", StatusCategory.DEBUFF, StatusShape.DURATION, false, false, false, 99),
            entry("Vulnerabilize", "Vulnérabilisation", StatusCategory.BUFF, StatusShape.VALUE_AND_DURATION, true, false, false, 99),
            entry("Weakness", "Affaibli", StatusCategory.DEBUFF, StatusShape.DURATION, false, false, false, 99),
            entry("Fortify", "Fortification", StatusCategory.BUFF, StatusShape.FIXED, false, false, false, 99),
            entry("Instability", "Instabilité", StatusCategory.DEBUFF, StatusShape.VALUE, false, false, false, 3),
            entry("Precision", "Précision", StatusCategory.BUFF, StatusShape.FIXED, false, false, false, 99),
                entry("Univers", "Univers", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
                entry("Megacristal", "Mégacristal", StatusCategory.DEBUFF, StatusShape.FIXED, true, true, false, 99),
                entry("Fragmentation", "Fragmentation", StatusCategory.DEBUFF, StatusShape.FIXED, true, false, false, 99),
                entry("Propulsion", "Propulsion", StatusCategory.BUFF, StatusShape.DURATION, true, true, false, 99),
                entry("Empathie", "Empathie", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
                entry("Avenir", "Avenir", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
                entry("Innocence", "Innocence", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
                entry("MesuresEnvergure", "Mesures d'envergure", StatusCategory.BUFF, StatusShape.FIXED, false, false, false, 99),
            entry("LamesArdentes", "Lames ardentes", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Sublimation", "Sublimation", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("AmeArtiste", "Âme d'artiste", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("GrandeOeuvre", "Grande œuvre", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            // Non cumulable : la valeur reste à 0, ce que FIXED garantit déjà.
            entry("ParcNaturel", "Parc naturel", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Pavillon", "Pavillon", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            // Non cumulable par une seconde pose : sa valeur est un compteur de combo que le
            // moteur seul fait monter et retomber (EffectResolver), pas une pile qu'on rejoue.
            entry("SuiteArithmetique", "Suite arithmétique", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 200),
            entry("MurOffensif", "Mur offensif", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("SeptiemeVitesse", "Septième vitesse", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("FreinMoteur", "Frein moteur", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            // Non cumulable : rien n'y lit de valeur, FIXED l'assure sans code à part.
            entry("Galvanisation", "Galvanisation", StatusCategory.BUFF, StatusShape.FIXED, true, false, false, 99),
            entry("Tenebres", "Ténèbres", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Adrenaline", "Adrénaline", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Miracle", "Miracle", StatusCategory.BUFF, StatusShape.VALUE, true, false, false, 99),
            entry("Unknown", "Unknown", StatusCategory.NEUTRAL, StatusShape.VALUE_AND_DURATION,
                    false, false, false, 99));

    private StatusCatalog() {
    }

    public static Optional<StatusDefinition> find(String statusType) {
        return Optional.ofNullable(statusType).map(BY_ID::get);
    }

    /**
     * @return the definition of this status, or a neutral fallback so an unknown status is stored
     *         and shown rather than silently reclassified
     */
    public static StatusDefinition of(String statusType) {
        return find(statusType).orElseGet(() -> BY_ID.get("Unknown"));
    }

    /**
     * Builds a status from authored numbers, keeping only the ones it reads.
     *
     * <p>A status that has no duration of its own is permanent rather than instantly expired,
     * which is the difference between an enemy walking in with its status and walking in without.
     */
    public static StatusInstance instantiate(
            String statusType,
            int value,
            int duration,
            String cardId,
            int index
    ) {
        StatusDefinition definition = of(statusType);
        int readDuration = definition.readsDuration() ? duration : -1;
        if ("FullMoonTemp".equals(statusType)) {
            readDuration = 1;
        }
        if ("Provocation".equals(statusType)
                || "Adrenaline".equals(statusType)
                || "Miracle".equals(statusType)
                || "Analyse".equals(statusType)
                || "Compilation".equals(statusType)) {
            readDuration = -1;
        }
        // A follow-up counts cards, not turns: an authored duration of zero is Unity's way of
        // saying it never runs out, the same as a status with no duration of its own.
        if (COUNTS_CARDS_NOT_TURNS.contains(statusType) && readDuration <= 0) {
            readDuration = -1;
        }
        readDuration = FIXED_DURATION.getOrDefault(statusType, readDuration);
        return new StatusInstance(
                statusType,
            "Crystallize".equals(statusType) ? 1 : definition.readsValue() ? value : 0,
                readDuration,
                cardId,
                index);
    }

    public static boolean isBuff(StatusInstance status) {
        return of(status.statusType()).isBuff(status.value());
    }

    public static boolean isDebuff(StatusInstance status) {
        return of(status.statusType()).isDebuff(status.value());
    }

    public static boolean isBuff(StatusInstance status, CardCatalog catalog) {
        return isFollowUp(status)
                ? !followUpCardIsDebuff(status, catalog)
                : isBuff(status);
    }

    public static boolean isDebuff(StatusInstance status, CardCatalog catalog) {
        return isFollowUp(status)
                ? followUpCardIsDebuff(status, catalog)
                : isDebuff(status);
    }

    private static boolean isFollowUp(StatusInstance status) {
        return COUNTS_CARDS_NOT_TURNS.contains(status.statusType());
    }

    /**
     * Whether a status still says something when the data gives it neither value nor duration.
     *
     * <p>Most do not: a Force of zero for zero turns is an empty slot in the data rather than a
     * status. Two kinds do. A status of FIXED shape carries neither number by design and decides
     * its own strength and length. And a follow-up reads both numbers as something other than a
     * quantity -- a value of zero is a threshold of one, a duration of zero is "never runs out"
     * ({@link #instantiate} turns it into -1) -- so zeroes are how one is ordinarily written.
     *
     * <p>Told apart here rather than at each caller, because getting it wrong is silent: the
     * status simply never exists, and nothing reports a status that was never applied.
     */
    public static boolean meansSomethingWithoutNumbers(String statusType) {
        if (statusType == null || statusType.isBlank()) {
            return false;
        }
        return COUNTS_CARDS_NOT_TURNS.contains(statusType)
                || FIXED_DURATION.containsKey(statusType)
                || of(statusType).shape() == StatusShape.FIXED;
    }

    private static boolean followUpCardIsDebuff(StatusInstance status, CardCatalog catalog) {
        if (catalog == null || status.cardId() == null || status.cardId().isBlank()) {
            return false;
        }
        CardDefinition card = catalog.find(status.cardId());
        return card != null && (card.hasTag(CardTag.STATUS) || card.hasTag(CardTag.CURSE));
    }

    /** @return true when the status is one the game declares, rather than engine bookkeeping */
    public static boolean isDeclared(StatusInstance status) {
        return find(status.statusType()).isPresent();
    }

    /** @return true when a card or a status effect may strip this instance */
    public static boolean isDispellable(StatusInstance status, boolean ignoringResistance) {
        // A relic remembering that it has already fired writes a marker the catalog never
        // declares. Letting a card strip it would hand the relic a second use, so anything the
        // game does not declare is the engine's own note and stays out of reach.
        if (!isDeclared(status)) {
            return false;
        }
        // The instance answers first when it has something to say: Unity marks the Fragile a
        // relic charges you with as unremovable, while an ordinary Fragile comes off.
        if (resistsEverything(status)) {
            return false;
        }
        if (status.removable() != null) {
            return status.removable() || ignoringResistance;
        }
        StatusDefinition definition = of(status.statusType());
        return !definition.framed() || ignoringResistance;
    }

    /**
     * @return true when this instance wears a frame, whether its kind is framed or a card framed
     *         it (FrameStatusesHandler writes the frame on the instance, not on the kind)
     */
    public static boolean isFramed(StatusInstance status) {
        if (resistsEverything(status)) {
            return true;
        }
        if (status.removable() != null) {
            return !status.removable();
        }
        return of(status.statusType()).framed();
    }

    /** @return true when nothing can strip this status, not even an effect ignoring resistance */
    public static boolean resistsEverything(StatusInstance status) {
        return status.resistsEverything() != null
                ? status.resistsEverything()
                : of(status.statusType()).goldFrame();
    }

    public static int count() {
        return BY_ID.size() - 1;
    }

    private static StatusDefinition entry(
            String id,
            String displayName,
            StatusCategory category,
            StatusShape shape,
            boolean framed,
            boolean goldFrame,
            boolean inextendable,
            int maxValue
    ) {
        return new StatusDefinition(
                id, displayName, category, shape, framed, goldFrame, inextendable, maxValue);
    }

    private static Map<String, StatusDefinition> index(StatusDefinition... definitions) {
        Map<String, StatusDefinition> byId = new LinkedHashMap<>();
        for (StatusDefinition definition : definitions) {
            byId.put(definition.id(), definition);
        }
        return Map.copyOf(byId);
    }
}
