package fr.insastronaute.webapi.sts.combat.engine.relic;

import fr.insastronaute.webapi.sts.combat.catalog.AnimationType;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.CardType;
import fr.insastronaute.webapi.sts.combat.catalog.EffectDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EffectType;
import fr.insastronaute.webapi.sts.combat.catalog.TargetingMode;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusCatalog;
import fr.insastronaute.webapi.sts.combat.engine.status.StatusDefinition;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Names a card after what it does, for the copies ITI tears off the other side.
 *
 * <p>The relic hands the player something the game never authored, so the copy cannot simply
 * borrow the name of the card it came from: two different enemy moves that both burn would arrive
 * under one title, and a move with no authored name would arrive under none. The name is built
 * from the effects instead -- "Annulame T" dispels a buff, cuts with a blade and hurries a turn,
 * and reading it back tells you so.
 *
 * <p>A blow's word is chosen by its animation rather than by its effect type, because Damage and
 * Multihit strike the same way and differ only in how often. That is the whole reason
 * {@link AnimationType} had to become something the catalog keeps.
 */
public final class StolenCardNaming {

    /** The effects that strike, and so the only ones that can head a name. */
    private static final Set<EffectType> STRIKES =
            EnumSet.of(EffectType.DAMAGE, EffectType.MULTIHIT);

    /** Past this much damage the name is marked; past {@link #HEAVY} it is marked harder. */
    private static final int NOTABLE = 9;
    private static final int HEAVY = 15;

    /**
     * What a card is called when it strikes with one element alone, hard enough to earn an X.
     *
     * <p>A card that does nothing but hit, and hit past {@link #HEAVY}, has earned a name of its
     * own rather than a word and a mark: "Sort X" became Ultima, and every other flavour now has
     * its equal.
     */
    private static final Map<AnimationType, String> ULTIMATES = Map.of(
            AnimationType.MAGIC, "Ultima",
            AnimationType.SLASH, "Omnislash",
            AnimationType.ICE, "Snowgrave",
            AnimationType.FIRE, "Firegrave",
            AnimationType.THUNDER, "Fulguration",
            AnimationType.LIGHT, "Sidéral",
            AnimationType.EXPLOSION_SMALL, "Explosion",
            AnimationType.EXPLOSION_LARGE, "Méga Atomnium",
            AnimationType.DEFAULT, "Extermination");

    /**
     * The ultimates that sweep the whole field rather than one target.
     *
     * <p>Everything that reaches this tier widens except the two that stay a single blow: a
     * blade, however overwhelming, still cuts one thing, and an unflavoured strike has nothing
     * about it that would spread.
     */
    private static final Set<AnimationType> FOCUSED_ULTIMATES =
            EnumSet.of(AnimationType.SLASH, AnimationType.DEFAULT);
    /** Ce que devient une carte volée dont rien, dans ses effets, ne se laisse nommer. */
    private static final String UNNAMED_NAME = "Maintenance";
    private static final String MAGIC_WORD = "Sort";
    private static final String HEAVY_MARK = "X";
    private static final String NOTABLE_MARK = "+";

    /**
     * How long a welded word may run before a two-effect card gives up on compounding.
     *
     * <p>Only two-effect cards consult it. At three effects the compound is forced -- the initials
     * have to hang off something -- but at two there is a real choice between "Tempolame" and
     * "Lame T", and the welded form is the better name right up until it turns into a mouthful:
     * "Destructobouclier" reads worse than the plain word and a letter.
     */
    private static final int COMPOUND_MAX_LENGTH = 13;

    /** How crowded a card has to be, or how heavy, before its copy costs two rather than one. */
    private static final int CROWDED = 4;
    private static final int COMPOUND = 2;

    /**
     * One effect's three faces: standing alone, leading a compound, or reduced to initials.
     *
     * @param word    the plain word, which heads the name when this effect is its subject
     * @param prefix  what this effect contributes when it leads a compound
     * @param acronym what is left of it once the name has no room for the word
     */
    private record Word(String word, String prefix, String acronym) {
    }

    // Acronyms collide on purpose -- Annihilation and Soin both answer to S, Glace and Gravitron
    // both to G, Delai and Blocus both to D. They are flavour rather than a code to be decoded,
    // so the authored table stands exactly as written instead of being disambiguated behind the
    // author's back.
    private static final Word DEFAULT_STRIKE = new Word("Frappe", "Multi-", "N");
    private static final Word THUNDER_STRIKE = new Word("Foudre", "Fulguro", "E");
    private static final Word ICE_STRIKE = new Word("Glace", "Cryo", "G");
    private static final Word FIRE_STRIKE = new Word("Flamme", "Pyro", "F");
    private static final Word LIGHT_STRIKE = new Word("Rayon", "Lux", "R");
    private static final Word MAGIC_STRIKE = new Word(MAGIC_WORD, "Magi", "M");
    private static final Word SLASH_STRIKE = new Word("Lame", "Tranch", "L");
    private static final Word SMALL_BLAST_STRIKE = new Word("Blast", "Blasto", "PE");
    private static final Word LARGE_BLAST_STRIKE = new Word("Explosion", "Explo", "GE");

    private static final Word ARMOR = new Word("Bouclier", "Armo", "A");
    private static final Word HEAL = new Word("Soin", "Vita", "S");
    private static final Word ADVANCE = new Word("Hâte", "Tempo", "T");
    private static final Word DELAY = new Word("Délai", "Somni", "D");
    private static final Word DELETE_TURN = new Word("Annihilation", "Annihi", "S");
    private static final Word DEBUFF = new Word("Blocus", "Destructo", "D");
    private static final Word BUFF = new Word("Boost", "Hyper", "B");
    private static final Word GRAVITY = new Word("Gravitron", "Gravi", "G");
    private static final Word DISPEL_BUFF = new Word("Dissipation", "Annu", "AB");
    private static final Word DISPEL_DEBUFF = new Word("Guérison", "Guéri", "AD");
    private static final Word DRAW = new Word("Drain", "Carto", "P");
    private static final Word ENERGY = new Word("Charge", "Dynamo", "E");
    private static final Word CUT_IN = new Word("Distorsion", "Disto", "I");

    private StolenCardNaming() {
    }

    /** A copy's whole identity, worked out from the effects of the card it was torn from. */
    public record StolenCard(String name, CardType type, int cost, TargetingMode targeting) {
    }

    /**
     * Where the copy is aimed.
     *
     * <p>Carried explicitly rather than inherited from the card it copies, because the two engines
     * do not agree on what an enemy move aims at: the server reads it off the effects, while
     * Unity's runtime enemy card calls every move {@code Enemy}. A copy of a move that only ever
     * armours its caster is aimed at its new owner -- the client offered an enemy, the server
     * wanted the player, and the play came back "Cible invalide".
     */
    private static TargetingMode targetingFor(List<EffectDefinition> effects) {
        boolean everythingPointsInward = !effects.isEmpty()
                && effects.stream().allMatch(EffectDefinition::targetSelf);
        return everythingPointsInward ? TargetingMode.SELF : TargetingMode.SINGLE_ENEMY;
    }

    /**
     * Reads a card and answers what its stolen copy is called, what family it belongs to, and what
     * it costs.
     *
     * @param definition the card the other side just played
     * @return the copy's identity; a card whose effects the naming table does not speak keeps its
     *         own name rather than arriving nameless
     */
    public static StolenCard describe(CardDefinition definition) {
        List<EffectDefinition> effects = definition.effects();
        CardType type = strikes(effects) ? CardType.ATTACK : CardType.SKILL;

        List<EffectDefinition> named = new ArrayList<>();
        for (EffectDefinition effect : effects) {
            if (wordFor(effect) != null) {
                named.add(effect);
            }
        }
        if (named.isEmpty()) {
            // Nothing here speaks: a card that only discards, or exhausts, or selects. It keeps
            // the name it came with -- when there is one to keep.
            return new StolenCard(
                    fallbackName(definition), type, costFor(0, ""), targetingFor(effects));
        }

        EffectDefinition head = headOf(named);
        List<EffectDefinition> rest = new ArrayList<>(named);
        rest.remove(head);

        String mark = markFor(effects);
        // A single overwhelming blow and nothing else: the card earns an ultimate, and most of
        // them widen to the whole field. Decided here rather than while assembling the name,
        // because reaching this tier changes what the card is aimed at as well as what it is
        // called.
        if (rest.isEmpty() && STRIKES.contains(head.type()) && HEAVY_MARK.equals(mark)) {
            AnimationType flavour = head.animationType();
            String ultimate = ULTIMATES.get(flavour);
            if (ultimate != null) {
                return new StolenCard(
                        ultimate,
                        type,
                        costFor(named.size(), mark),
                        FOCUSED_ULTIMATES.contains(flavour)
                                ? targetingFor(effects)
                                : TargetingMode.ALL_ENEMIES);
            }
        }
        return new StolenCard(assemble(head, rest, mark), type, costFor(named.size(), mark),
                targetingFor(effects));
    }

    /**
     * Ce qu'on appelle une carte dont aucun effet ne se laisse nommer.
     *
     * <p>Un mouvement d'ennemi écrit à même la fiche n'a pas de nom : {@code EnemyMoveParser} lui
     * fabrique un identifiant, {@code enemy-move:gobelin:2}, et la définition prend cet
     * identifiant pour nom faute d'autre chose. Le rendre tel quel au joueur affichait cet
     * identifiant sur la carte. Un mouvement qui désigne une vraie carte, lui, en porte le nom
     * authentique, et celui-là se garde.
     */
    private static String fallbackName(CardDefinition definition) {
        String name = definition.name();
        if (name == null || name.isBlank() || name.equals(definition.id())) {
            return UNNAMED_NAME;
        }
        return name;
    }

    /**
     * Which effect the name is built around.
     *
     * <p>A blow always takes the subject, whatever its place in the list, because that is what the
     * card is for -- the first blow, when a card strikes more than once. Failing any blow, the
     * first effect with a word to give.
     */
    private static EffectDefinition headOf(List<EffectDefinition> named) {
        for (EffectDefinition effect : named) {
            if (STRIKES.contains(effect.type())) {
                return effect;
            }
        }
        return named.getFirst();
    }

    /**
     * Puts the pieces together.
     *
     * <p>Three effects or more and the name compounds: the next effect's prefix welded onto the
     * subject's word, everything after it reduced to initials.
     *
     * <p>Two effects could go either way, and which reads better depends on the words themselves.
     * The compound wins by default, because a made-up word is a better name than a word and a
     * bare letter -- but only while it stays short enough to say. Past
     * {@link #COMPOUND_MAX_LENGTH} the second effect drops back to its initial.
     *
     * <p>One effect has nothing to weld to anything, and stands as its own word.
     */
    private static String assemble(
            EffectDefinition head,
            List<EffectDefinition> rest,
            String mark
    ) {
        Word headWord = wordFor(head);
        String stem;
        List<EffectDefinition> initials;
        if (rest.size() >= 2) {
            stem = compound(rest.getFirst(), headWord);
            initials = rest.subList(1, rest.size());
        } else if (rest.size() == 1 && compound(rest.getFirst(), headWord).length()
                <= COMPOUND_MAX_LENGTH) {
            stem = compound(rest.getFirst(), headWord);
            initials = List.of();
        } else {
            stem = headWord.word();
            initials = rest;
        }

        StringBuilder acronym = new StringBuilder();
        for (EffectDefinition effect : initials) {
            acronym.append(wordFor(effect).acronym());
        }

        String assembled = acronym.isEmpty() ? stem : stem + " " + acronym;
        return assembled + mark;
    }

    /** One effect's prefix welded onto the subject's word, which is the compound form. */
    private static String compound(EffectDefinition prefixSource, Word headWord) {
        return wordFor(prefixSource).prefix() + headWord.word().toLowerCase(Locale.FRENCH);
    }

    /**
     * How hard the card hits, told in one character.
     *
     * <p>Every blow on the card counts, and a Multihit counts for all of its hits: what the mark
     * answers is what the copy will do when it is played, not what its heaviest single effect
     * does.
     */
    private static String markFor(List<EffectDefinition> effects) {
        int total = 0;
        for (EffectDefinition effect : effects) {
            if (effect.type() == EffectType.MULTIHIT) {
                total += effect.value() * Math.max(1, effect.duration());
            } else if (effect.type() == EffectType.DAMAGE) {
                total += effect.value();
            }
        }
        if (total > HEAVY) {
            return HEAVY_MARK;
        }
        return total > NOTABLE ? NOTABLE_MARK : "";
    }

    /**
     * What the copy costs its new owner.
     *
     * <p>A stolen card is cheap by design -- it is gone at the end of the turn either way -- so
     * the price only rises for the ones that would otherwise be a whole turn's work for a single
     * point: the crowded ones, and the ones that hit hard enough to be marked.
     */
    private static int costFor(int namedEffects, String mark) {
        boolean crowded = namedEffects >= CROWDED;
        boolean heavyAndCompound = namedEffects >= COMPOUND && HEAVY_MARK.equals(mark);
        return crowded || heavyAndCompound ? 2 : 1;
    }

    private static boolean strikes(List<EffectDefinition> effects) {
        return effects.stream().anyMatch(effect -> STRIKES.contains(effect.type()));
    }

    /** @return the three faces of this effect, or null when the table has no word for it */
    private static Word wordFor(EffectDefinition effect) {
        if (STRIKES.contains(effect.type())) {
            return switch (effect.animationType()) {
                case SLASH -> SLASH_STRIKE;
                case MAGIC -> MAGIC_STRIKE;
                case FIRE -> FIRE_STRIKE;
                case ICE -> ICE_STRIKE;
                case THUNDER -> THUNDER_STRIKE;
                case LIGHT -> LIGHT_STRIKE;
                case EXPLOSION_SMALL -> SMALL_BLAST_STRIKE;
                case EXPLOSION_LARGE -> LARGE_BLAST_STRIKE;
                case DEFAULT -> DEFAULT_STRIKE;
            };
        }
        return switch (effect.type()) {
            case ARMOR -> ARMOR;
            case HEAL -> HEAL;
            case ADVANCE_TURN -> ADVANCE;
            case DELAY_TURN -> DELAY;
            case DELETE_NEXT_TURN -> DELETE_TURN;
            case GRAVITY -> GRAVITY;
            case DISPEL_BUFF -> DISPEL_BUFF;
            case DISPEL_DEBUFF -> DISPEL_DEBUFF;
            case DRAW -> DRAW;
            case GAIN_ENERGY -> ENERGY;
            case CUT_IN_TURN -> CUT_IN;
            case STATUS -> statusWord(effect);
            default -> null;
        };
    }

    /**
     * A status is named by which way it cuts, not by which status it is.
     *
     * <p>An unknown status type is left unnamed rather than guessed at: calling a buff a Blocus
     * would read as the opposite of what the card does.
     */
    private static Word statusWord(EffectDefinition effect) {
        StatusDefinition status = StatusCatalog.find(effect.statusType()).orElse(null);
        if (status == null) {
            return null;
        }
        return status.isDebuff(effect.value()) ? DEBUFF : BUFF;
    }
}
