package fr.insastronaute.webapi.common.adminaudit.repository;

import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionEvent;
import fr.insastronaute.webapi.common.adminaudit.domain.AdminActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AdminActionEventRepository extends JpaRepository<AdminActionEvent, UUID> {

    List<AdminActionEvent> findByTargetIdOrderByOccurredAtDesc(UUID targetId);

    /**
     * Le registre retient-il encore ce joueur, d'un cote ou de l'autre ?
     *
     * <p>Les deux colonnes sont en {@code ON DELETE RESTRICT} : tant que l'une d'elles le
     * designe, sa ligne ne peut pas disparaitre. La question porte sur l'auteur autant que
     * sur la cible -- un ancien administrateur a signe des gestes sur d'autres.</p>
     */
    @Query("""
            select count(event) > 0 from AdminActionEvent event
            where event.actor.id = :userId or event.target.id = :userId
            """)
    boolean isReferenced(@Param("userId") UUID userId);

    /**
     * La page du registre, du plus recent au plus ancien.
     *
     * <p>Les deux {@code join fetch} sont ce qui tient la promesse d'une seule
     * requete pour toute la page : sans eux, l'auteur et la cible sont des
     * proxys paresseux et lire leur nom d'affichage ferait une requete par
     * ligne, soit quarante-et-une pour une page de vingt. Les deux
     * associations sont mono-valuees, donc la limite reste dans le SQL et la
     * pagination ne se fait pas en memoire.</p>
     *
     * <p>Les jointures sont <strong>explicites et externes</strong>, et les
     * memes des deux cotes. Naviguer {@code event.target.id} dans le
     * {@code where} laisserait a Hibernate le choix d'une jointure interne,
     * qui ecarterait les lignes de portee globale meme quand aucun filtre de
     * cible n'est demande - et la requete de comptage et celle de la page
     * pourraient ne pas faire le meme choix.</p>
     *
     * <p>Tous les filtres sont types, et facultatifs sauf {@code actions}, que
     * l'appelant construit toujours non vide. Un {@code targetUserId} ne
     * ramene jamais une ligne de portee globale : sa colonne est nulle, et
     * {@code null = ?} n'est jamais vrai.</p>
     *
     * <p>Le {@code cast} sur les deux bornes de periode n'est pas decoratif :
     * <strong>sans lui, la requete ne s'execute pas du tout sur
     * PostgreSQL.</strong> Un marqueur qui n'apparait que sous
     * {@code ? is null} ne dit rien de son type - {@code $n IS NULL} est vrai
     * ou faux pour n'importe quel type - et PostgreSQL refuse alors de
     * preparer l'ordre : <em>could not determine data type of parameter</em>.
     * Le pilote comble ce trou pour un {@code UUID}, dont il connait la classe
     * Java, mais pas pour un instant, d'ou l'asymetrie avec les deux filtres
     * d'identifiant juste au-dessus. Le {@code cast} nomme le type dans le SQL
     * lui-meme et clot la question, que la borne soit fournie ou non.</p>
     *
     * <p>Et la suite de tests ne rattrapera pas sa suppression : elle tourne
     * sur H2, qui accepte le marqueur sans type. Ce cast se verifie contre un
     * PostgreSQL, ou pas du tout.</p>
     *
     * @param from borne basse incluse, ou {@code null}
     * @param to   borne haute <strong>exclue</strong>, ou {@code null} : deux
     *             periodes qui se suivent pavent alors le temps sans se
     *             recouvrir ni laisser de trou
     */
    @Query(
            value = """
                    select event from AdminActionEvent event
                    left join fetch event.actor actor
                    left join fetch event.target target
                    where event.action in :actions
                      and (:actorUserId is null or actor.id = :actorUserId)
                      and (:targetUserId is null or target.id = :targetUserId)
                      and (cast(:from as Instant) is null or event.occurredAt >= :from)
                      and (cast(:to as Instant) is null or event.occurredAt < :to)
                    order by event.occurredAt desc, event.id desc
                    """,
            countQuery = """
                    select count(event) from AdminActionEvent event
                    left join event.actor actor
                    left join event.target target
                    where event.action in :actions
                      and (:actorUserId is null or actor.id = :actorUserId)
                      and (:targetUserId is null or target.id = :targetUserId)
                      and (cast(:from as Instant) is null or event.occurredAt >= :from)
                      and (cast(:to as Instant) is null or event.occurredAt < :to)
                    """
    )
    Page<AdminActionEvent> search(
            @Param("actions") Collection<AdminActionType> actions,
            @Param("actorUserId") UUID actorUserId,
            @Param("targetUserId") UUID targetUserId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query(value = """
            select event from AdminActionEvent event
            left join fetch event.actor actor
            left join fetch event.target target
            where event.action in :actions
              and (:actorUserId is null or actor.id = :actorUserId)
              and (:playerUserId is null or target.id = :playerUserId
                   or lower(event.detailsJson) like :playerPattern)
              and (:challengePattern is null
                   or lower(event.detailsJson) like :challengePattern escape '!')
              and (cast(:from as Instant) is null or event.occurredAt >= :from)
              and (cast(:to as Instant) is null or event.occurredAt < :to)
            order by event.occurredAt desc, event.id desc
            """, countQuery = """
            select count(event) from AdminActionEvent event
            left join event.actor actor
            left join event.target target
            where event.action in :actions
              and (:actorUserId is null or actor.id = :actorUserId)
              and (:playerUserId is null or target.id = :playerUserId
                   or lower(event.detailsJson) like :playerPattern)
              and (:challengePattern is null
                   or lower(event.detailsJson) like :challengePattern escape '!')
              and (cast(:from as Instant) is null or event.occurredAt >= :from)
              and (cast(:to as Instant) is null or event.occurredAt < :to)
            """)
    Page<AdminActionEvent> searchModeratedChallengesDesc(
            @Param("actions") Collection<AdminActionType> actions,
            @Param("actorUserId") UUID actorUserId,
            @Param("playerUserId") UUID playerUserId,
            @Param("playerPattern") String playerPattern,
            @Param("challengePattern") String challengePattern,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query(value = """
            select event from AdminActionEvent event
            left join fetch event.actor actor
            left join fetch event.target target
            where event.action in :actions
              and (:actorUserId is null or actor.id = :actorUserId)
              and (:playerUserId is null or target.id = :playerUserId
                   or lower(event.detailsJson) like :playerPattern)
              and (:challengePattern is null
                   or lower(event.detailsJson) like :challengePattern escape '!')
              and (cast(:from as Instant) is null or event.occurredAt >= :from)
              and (cast(:to as Instant) is null or event.occurredAt < :to)
            order by event.occurredAt asc, event.id asc
            """, countQuery = """
            select count(event) from AdminActionEvent event
            left join event.actor actor
            left join event.target target
            where event.action in :actions
              and (:actorUserId is null or actor.id = :actorUserId)
              and (:playerUserId is null or target.id = :playerUserId
                   or lower(event.detailsJson) like :playerPattern)
              and (:challengePattern is null
                   or lower(event.detailsJson) like :challengePattern escape '!')
              and (cast(:from as Instant) is null or event.occurredAt >= :from)
              and (cast(:to as Instant) is null or event.occurredAt < :to)
            """)
    Page<AdminActionEvent> searchModeratedChallengesAsc(
            @Param("actions") Collection<AdminActionType> actions,
            @Param("actorUserId") UUID actorUserId,
            @Param("playerUserId") UUID playerUserId,
            @Param("playerPattern") String playerPattern,
            @Param("challengePattern") String challengePattern,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );
}
