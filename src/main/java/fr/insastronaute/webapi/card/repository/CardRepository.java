package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByEnabledTrue();

    /**
     * Ce que « toute la collection » veut dire pour un joueur.
     *
     * <p>Le catalogue entier ne le dit pas : la carte par défaut appartient à
     * tout le monde d'office, les catégories cachées et de jeu ne se collectionnent
     * pas, et la carte bonus — celle que le code goofy donne — n'appartient à aucune
     * collection. Les compter rendait un défi à 100 % inatteignable, et l'écran du
     * joueur affichait déjà un autre total que le défi.</p>
     *
     * <p>C'est la définition de l'écran personnel, extensions comprises.</p>
     */
    @Query("""
            select count(card)
            from Card card
            where card.enabled = true
              and card.defaultCard = false
              and (card.categoryRef is null
                   or (card.categoryRef.hidden = false and card.categoryRef.game = false))
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = card.id
                )
            """)
    long countPersonalCollectionCards();

    /**
     * Le même compte pour la collection d'équipe : sans les extensions.
     *
     * <p>La vue d'équipe les écarte déjà. Un objectif mesuré sur l'équipe doit viser
     * ce que l'équipe voit, sinon il reste ouvert sur des cartes que son écran ne
     * lui montre pas.</p>
     */
    @Query("""
            select count(card)
            from Card card
            where card.enabled = true
              and card.defaultCard = false
              and (card.categoryRef is null
                   or (card.categoryRef.hidden = false
                       and card.categoryRef.game = false
                       and card.categoryRef.extension = false))
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = card.id
                )
            """)
    long countTeamCollectionCards();

    Optional<Card> findByNameIgnoreCase(String name);

    List<Card> findByCategoryRef_IdAndDefaultCardFalse(UUID categoryId);

    boolean existsByCategoryRef_IdAndDefaultCardFalse(UUID categoryId);

    List<Card> findByTypeRef_Id(UUID typeId);

    Optional<Card> findByDefaultCardTrue();

    boolean existsByDefaultCardTrue();

    // ---- Achievement key phrases -------------------------------------------

    /**
     * Ce que « toute cette catégorie » veut dire.
     *
     * <p>Pas de filtre sur les extensions ici : viser une catégorie, c'est la
     * nommer, extension ou pas. La carte par défaut et la carte bonus en sortent,
     * comme partout ailleurs.</p>
     */
    @Query("""
            select count(card)
            from Card card
            where card.enabled = true
              and card.defaultCard = false
              and card.categoryRef.id = :categoryId
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = card.id
                )
            """)
    long countCollectionCardsInCategory(@Param("categoryId") UUID categoryId);

    /**
     * Ce que « tout ce type » veut dire.
     *
     * <p>Pas de filtre sur les extensions, comme pour une categorie : viser un type,
     * c'est le nommer. La carte par defaut et la carte bonus en sortent, comme
     * partout ailleurs.</p>
     */
    @Query("""
            select count(card)
            from Card card
            where card.enabled = true
              and card.defaultCard = false
              and card.typeRef.id = :typeId
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = card.id
                )
            """)
    long countCollectionCardsOfType(@Param("typeId") UUID typeId);
}
