package fr.insastronaute.webapi.place.repository;

import fr.insastronaute.webapi.place.domain.PlacePixelPlacement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PlacePixelPlacementRepository extends JpaRepository<PlacePixelPlacement, Long> {

    /** L'historique d'une case, du plus récent au plus ancien. */
    List<PlacePixelPlacement> findByGenerationAndXAndYOrderByPlacedAtDesc(
            String generation, int x, int y, Pageable pageable);

    /**
     * Les auteurs d'une zone, dernière pose par case.
     *
     * <p>Une image choquante fait plusieurs dizaines de pixels : les regarder un
     * par un ferait autant d'allers-retours, et la modération se ferait sur une
     * seule case au lieu du dessin.</p>
     */
    @Query("""
            select p from PlacePixelPlacement p
            where p.generation = :generation
              and p.x between :minX and :maxX
              and p.y between :minY and :maxY
              and p.placedAt = (
                  select max(latest.placedAt) from PlacePixelPlacement latest
                  where latest.generation = p.generation and latest.x = p.x and latest.y = p.y
              )
            """)
    List<PlacePixelPlacement> findLatestInArea(
            @Param("generation") String generation,
            @Param("minX") int minX,
            @Param("maxX") int maxX,
            @Param("minY") int minY,
            @Param("maxY") int maxY);

    long countByUserId(UUID userId);

    /**
     * Ce que chaque joueur a pose pendant l'evenement.
     *
     * <p>Le filtre porte sur l'heure, pas sur la generation : un redimensionnement
     * du canevas en ouvre une nouvelle, et compter par generation effacerait du
     * classement tout ce qui a ete pose avant. Les deux fresques sont confondues,
     * un pixel valant un pixel ou qu'il soit pose.</p>
     */
    @Query("""
            select new fr.insastronaute.webapi.place.repository.PlacePixelCount(p.userId, count(p))
            from PlacePixelPlacement p
            where p.placedAt >= :from and p.placedAt < :to
            group by p.userId
            """)
    List<PlacePixelCount> countPixelsByUserBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Les poses d'une periode, dans l'ordre ou elles ont ete faites.
     *
     * <p>De quoi rejouer la fresque : la table est en ajout seul, donc rejouer ses
     * lignes par-dessus un canevas vide reconstruit n'importe quel instant de
     * l'evenement.</p>
     *
     * <p>Une seule generation a la fois, et c'est la raison : un redimensionnement en
     * ouvre une nouvelle et recentre l'image, si bien qu'une coordonnee d'avant ne
     * designe plus la meme case. Melanger deux generations produirait une video
     * decalee, sans que rien ne le signale.</p>
     *
     * <p>Paginee : un evenement peut compter des centaines de milliers de poses, et
     * les charger d'un bloc ferait dependre la memoire du serveur de la duree de
     * l'evenement. L'identifiant departage deux poses de la meme milliseconde, sans
     * quoi la pagination pourrait en sauter ou en repeter.</p>
     */
    @Query("""
            select new fr.insastronaute.webapi.place.repository.PlaceTimelapsePixel(p.x, p.y, p.color)
            from PlacePixelPlacement p
            where p.generation = :generation
              and p.placedAt >= :from and p.placedAt < :to
            order by p.placedAt asc, p.id asc
            """)
    List<PlaceTimelapsePixel> findReplay(
            @Param("generation") String generation,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Les memes poses, peintes aux couleurs des departements de leurs auteurs.
     *
     * <p>Le timelapse montre alors qui tenait quoi au fil des heures, la ou le rejeu
     * ordinaire montre la fresque telle qu'elle s'est faite.</p>
     *
     * <p>Une pose d'un joueur sans departement prend le gris de la vue des
     * departements : la meme case y est deja grise, et lui donner sa couleur d'origine
     * ferait apparaitre un departement qui n'existe pas.</p>
     */
    @Query("""
            select new fr.insastronaute.webapi.place.repository.PlaceTimelapsePixel(
                p.x, p.y, coalesce(d.color, :unattributed))
            from PlacePixelPlacement p
            join AppUser u on u.id = p.userId
            left join Department d on d.id = u.departmentId
            where p.generation = :generation
              and p.placedAt >= :from and p.placedAt < :to
            order by p.placedAt asc, p.id asc
            """)
    List<PlaceTimelapsePixel> findReplayByDepartment(
            @Param("generation") String generation,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("unattributed") String unattributed,
            Pageable pageable);

    /**
     * La dernière pose de chaque case d'une génération, et le département de son auteur.
     *
     * <p>Toute la fresque des départements en une lecture, sur la même règle que
     * {@link #findLatestInArea} : la pose la plus récente de chaque case, que l'index
     * (generation, x, y, placed_at DESC) trouve sans trier la table. Deux poses au même
     * instant rendent la case deux fois ; l'appelant n'en compte qu'une.</p>
     *
     * <p>JPQL et non SQL natif : en natif, le type de {@code department_id} dépend du
     * pilote — un UUID sous Postgres, des octets sous H2 — et la projection ne sait pas
     * convertir le second. Hibernate, lui, connaît le type de l'attribut.</p>
     *
     * <p>Le département est celui que l'auteur a aujourd'hui : un joueur rattaché
     * après coup colore aussi ses pixels d'avant.</p>
     */
    @Query("""
            select p.x as x, p.y as y, p.color as color, u.departmentId as departmentId
            from PlacePixelPlacement p
            join AppUser u on u.id = p.userId
            where p.generation = :generation
              and p.placedAt = (
                  select max(latest.placedAt) from PlacePixelPlacement latest
                  where latest.generation = p.generation and latest.x = p.x and latest.y = p.y
              )
            """)
    List<LatestCellPlacement> findLatestCellsWithDepartment(@Param("generation") String generation);

    interface LatestCellPlacement {
        int getX();

        int getY();

        String getColor();

        UUID getDepartmentId();
    }
}
