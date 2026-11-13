package fr.insastronaute.webapi.sts.combat.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.insastronaute.webapi.sts.combat.catalog.CardCatalog;
import fr.insastronaute.webapi.sts.combat.catalog.CardDefinition;
import fr.insastronaute.webapi.sts.combat.catalog.EnemyMoveParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;

/**
 * Comment on lit le repertoire de coups d'un ennemi dans les donnees de jeu.
 *
 * <p>Ces regles-la ne sont pas evidentes et elles ne sont ecrites qu'ici : un ennemi declare ses
 * coups soit dans {@code movePattern} — la forme complete, avec les enchainements et les poids —
 * soit dans {@code patternCardIds}, une simple suite de cartes ou chaque coup mene au suivant.
 * La premiere forme, quand elle est presente et non vide, l'emporte : elle dit strictement plus.
 *
 * <p>Elles vivent ici plutot que dans le service de run parce que le raid en a besoin des deux
 * cotes. Un boss de raid est un ennemi des memes donnees, joue par le meme
 * {@link PatternEnemyPolicy}, et une seconde lecture ecrite ailleurs aurait derive de celle-ci —
 * c'est-a-dire qu'un ennemi se serait battu differemment selon qu'on le rencontre en run ou en
 * raid, sans que rien ne le signale.
 *
 * <p>Un coup illisible est saute plutot que devine, et un ennemi peut donc se retrouver sans
 * aucun coup : {@link PatternEnemyPolicy} finit alors son tour sans agir, ce qui garde le combat
 * jouable. C'est un defaut de contenu, pas une panne, et c'est a l'appelant de decider s'il le
 * signale — d'ou {@link Diagnostics}, qui ne sait rien de la ou l'on a trouve cet ennemi.
 */
public final class EnemyPatternReader {

    /**
     * Ce qu'il y a a dire d'un ennemi mal declare.
     *
     * <p>Passe par l'appelant parce que lui seul sait nommer le contexte — une run et son
     * encounter, un raid et sa bataille — et surtout parce que lui seul sait si ce defaut a deja
     * ete signale. Un motif est relu a chaque commande, pas une fois par combat : signaler sans
     * retenue noierait le journal du combat que ces lignes servent a expliquer.
     */
    public interface Diagnostics {

        /**
         * Un coup declare que rien ne permet de jouer.
         *
         * @param field le champ dont il vient — {@code movePattern} ou {@code patternCardIds} —
         *              parce qu'un ennemi peut declarer ses coups dans l'un ou dans l'autre, et
         *              qu'un message qui ne dit pas lequel envoie chercher dans le mauvais.
         */
        void moveDropped(String enemyId, String field, int moveIndex, String reason);

        /** L'ennemi n'a fini avec aucun coup jouable, sur {@code declaredMoves} declares. */
        void noUsableMove(String enemyId, int declaredMoves);

        /** Celui qui ne dit rien. */
        Diagnostics SILENT = new Diagnostics() {
            @Override
            public void moveDropped(String enemyId, String field, int moveIndex, String reason) {
            }

            @Override
            public void noUsableMove(String enemyId, int declaredMoves) {
            }
        };
    }

    /** Un repertoire lu, et de quoi dire ce qui a ete perdu en le lisant. */
    public record Pattern(List<PatternEnemyPolicy.EnemyMoveNode> moves, int declaredMoves) {

        public Pattern {
            moves = List.copyOf(Objects.requireNonNull(moves, "moves"));
        }

        public boolean isEmpty() {
            return moves.isEmpty();
        }
    }

    private EnemyPatternReader() {
    }

    /**
     * Le repertoire de {@code enemy}, lu contre {@code catalog}.
     *
     * @param catalog le catalogue ou les cartes des coups sont resolues
     * @param enemy   la definition de l'ennemi, telle qu'elle est dans les donnees de jeu
     * @param enemyId son identifiant, pour les messages
     */
    public static Pattern read(
            CardCatalog catalog,
            JsonNode enemy,
            String enemyId,
            EnemyMoveParser moveParser,
            Diagnostics diagnostics
    ) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(enemy, "enemy");
        Objects.requireNonNull(moveParser, "moveParser");
        Objects.requireNonNull(diagnostics, "diagnostics");

        List<PatternEnemyPolicy.EnemyMoveNode> moves = new ArrayList<>();
        int declaredMoves = 0;

        JsonNode movePattern = enemy.path("movePattern");
        if (movePattern.isArray() && !movePattern.isEmpty()) {
            declaredMoves = movePattern.size();
            // Les `nextMoveIndices` designent des positions dans la liste construite, pas
            // dans celle declaree : un coup ecarte decale donc tous les suivants, et les
            // transitions pointent en silence a cote. On garde la correspondance entre les
            // deux numerotations pour les remettre d'accord une fois la liste connue.
            Map<Integer, Integer> keptByDeclaredIndex = new LinkedHashMap<>();
            List<List<Integer>> declaredSuccessors = new ArrayList<>();

            for (int index = 0; index < movePattern.size(); index++) {
                JsonNode move = movePattern.get(index);
                CardDefinition definition = moveParser.parseMove(catalog, move, enemyId, index);
                if (definition == null) {
                    diagnostics.moveDropped(enemyId, "movePattern", index,
                            "it declares neither a cardId nor any effect, so there is nothing"
                                    + " for the enemy to play");
                    continue;
                }
                List<Integer> nextIndices = new ArrayList<>();
                for (JsonNode next : move.path("nextMoveIndices")) {
                    nextIndices.add(next.asInt());
                }
                keptByDeclaredIndex.put(index, moves.size());
                declaredSuccessors.add(nextIndices);
                moves.add(new PatternEnemyPolicy.EnemyMoveNode(
                        definition.id(), nextIndices, move.path("weight").asInt(1)));
            }

            if (keptByDeclaredIndex.size() < declaredMoves) {
                remapSuccessors(moves, declaredSuccessors, keptByDeclaredIndex);
            }
        } else {
            JsonNode patternCardIds = enemy.path("patternCardIds");
            if (patternCardIds.isArray() && !patternCardIds.isEmpty()) {
                declaredMoves = patternCardIds.size();
                for (int index = 0; index < patternCardIds.size(); index++) {
                    String cardId = patternCardIds.get(index).asText("").trim();
                    if (cardId.isBlank()) {
                        diagnostics.moveDropped(enemyId, "patternCardIds", index,
                                "the card id is blank");
                        continue;
                    }
                    moves.add(new PatternEnemyPolicy.EnemyMoveNode(cardId, List.of(), 1));
                }
            }
        }

        if (moves.isEmpty()) {
            diagnostics.noUsableMove(enemyId, declaredMoves);
        }
        return new Pattern(moves, declaredMoves);
    }

    /**
     * Remettre les transitions d'accord avec la liste reellement construite.
     *
     * <p>Un coup ecarte laisse un trou dans la numerotation : sans cette reprise, chaque
     * transition posterieure vise le coup d'apres, et celles qui debordent sont
     * silencieusement ignorees -- l'ennemi joue un enchainement que personne n'a ecrit.
     * Une transition qui visait le coup ecarte, elle, ne vise plus rien : la laisser
     * pointer sur son voisin inventerait un enchainement tout autant.</p>
     */
    private static void remapSuccessors(
            List<PatternEnemyPolicy.EnemyMoveNode> moves,
            List<List<Integer>> declaredSuccessors,
            Map<Integer, Integer> keptByDeclaredIndex
    ) {
        for (int position = 0; position < moves.size(); position++) {
            List<Integer> remapped = declaredSuccessors.get(position).stream()
                    .map(keptByDeclaredIndex::get)
                    .filter(Objects::nonNull)
                    .toList();
            PatternEnemyPolicy.EnemyMoveNode node = moves.get(position);
            moves.set(position, new PatternEnemyPolicy.EnemyMoveNode(
                    node.cardId(), remapped, node.weight()));
        }
    }

    /**
     * Les cartes que les coups de {@code enemy} nomment, a verser au catalogue du combat.
     *
     * <p>Sans elles, un coup du boss atteint la table sans definition derriere lui et se fait
     * refuser au moment ou il est joue. C'est la meme necessite qu'en run, ou le catalogue d'un
     * combat est reduit a ce que ce combat nomme.
     */
    public static List<CardDefinition> moveDefinitions(
            CardCatalog baseCatalog,
            JsonNode enemy,
            String enemyId,
            EnemyMoveParser moveParser
    ) {
        Objects.requireNonNull(baseCatalog, "baseCatalog");
        Objects.requireNonNull(enemy, "enemy");
        Objects.requireNonNull(moveParser, "moveParser");

        List<CardDefinition> definitions = new ArrayList<>();
        JsonNode movePattern = enemy.path("movePattern");
        if (movePattern.isArray()) {
            for (int index = 0; index < movePattern.size(); index++) {
                CardDefinition definition =
                        moveParser.parseMove(baseCatalog, movePattern.get(index), enemyId, index);
                if (definition != null) {
                    definitions.add(definition);
                }
            }
        }
        return List.copyOf(definitions);
    }

    /**
     * La definition de l'ennemi {@code enemyId} parmi {@code enemies}.
     *
     * <p>L'identifiant est cherche sur {@code id} puis sur {@code enemyName}, parce que les
     * donnees de jeu nomment les ennemis des deux facons selon leur anciennete.
     *
     * @throws IllegalArgumentException quand aucun ennemi ne repond a cet identifiant
     */
    public static JsonNode findEnemy(ArrayNode enemies, String enemyId) {
        Objects.requireNonNull(enemies, "enemies");
        for (JsonNode enemy : enemies) {
            if (enemyId.equals(enemy.path("id").asText("").trim())
                    || enemyId.equals(enemy.path("enemyName").asText("").trim())) {
                return enemy;
            }
        }
        throw new IllegalArgumentException("unknown enemy '" + enemyId + "'");
    }
}
