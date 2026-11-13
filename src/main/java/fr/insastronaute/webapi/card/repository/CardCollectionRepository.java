package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.CardCollection;
import fr.insastronaute.webapi.card.domain.CardCollectionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

/**
 * Les comptes de cette interface disent tous la meme chose de la collection : une
 * carte activee, qui n'est ni la carte par defaut, ni d'une categorie cachee ou de
 * jeu, ni la carte bonus du code goofy. Les objectifs mesures sur l'equipe ecartent
 * en plus les extensions, que la vue d'equipe n'affiche pas.
 *
 * <p>La regle est repetee dans chaque requete faute de pouvoir partager un fragment
 * de JPQL. Son pendant cote catalogue vit dans
 * {@link CardRepository#countPersonalCollectionCards()} : numerateur et denominateur
 * doivent bouger ensemble, sinon un joueur depasse 100 % ou n'y arrive jamais.</p>
 */
@Repository
public interface CardCollectionRepository extends JpaRepository<CardCollection, UUID> {

    /**
     * Les cartes viennent avec, comme dans la variante d'équipe juste en dessous.
     *
     * <p>Cette requête était dérivée, donc sans jointure : la carte restait un proxy
     * paresseux. UserController.getProfile n'étant pas transactionnel, la session se
     * fermait avec l'appel au dépôt, et le calcul des points de collection faisait
     * éclater le proxy — LazyInitializationException sur /api/users/profile.
     *
     * <p>Là où une transaction couvrait l'accès, comme CardService.getDeck, rien ne
     * cassait : ça coûtait simplement une requête par ligne de collection.
     */
    @Query("""
            select collection
            from CardCollection collection
            join fetch collection.card card
            left join fetch card.categoryRef
            where collection.user.id = :userId
              and collection.collectionType = :collectionType
            """)
    List<CardCollection> findByUserIdAndCollectionType(
            @Param("userId") UUID userId,
            @Param("collectionType") CardCollectionType collectionType
    );

    @Query("""
            select collection
            from CardCollection collection
            join fetch collection.card card
            left join fetch card.categoryRef
            where collection.user.id in :userIds
              and collection.collectionType = :collectionType
            """)
    List<CardCollection> findByUserIdsAndCollectionType(
            @Param("userIds") Collection<UUID> userIds,
            @Param("collectionType") CardCollectionType collectionType
    );

    @Query("""
            select count(collection)
            from CardCollection collection
            where collection.user.id = :userId
              and collection.collectionType = :collectionType
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and (collection.card.categoryRef is null
                   or (collection.card.categoryRef.hidden = false
                       and collection.card.categoryRef.game = false))
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )
            """)
    long countOwnedEnabledCards(
            @Param("userId") UUID userId,
            @Param("collectionType") CardCollectionType collectionType
    );

    @Query("""
            select collection
            from CardCollection collection
            join fetch collection.card
            where collection.user.id = :userId
            order by collection.collectionType asc, collection.updatedAt desc
            """)
    List<CardCollection> findByUserIdForAdmin(@Param("userId") UUID userId);

    Optional<CardCollection> findByUserIdAndCardIdAndCollectionType(
            UUID userId,
            UUID cardId,
            CardCollectionType collectionType
    );

    @Query("""
            select collection
            from CardCollection collection
            join fetch collection.user
            join fetch collection.card card
            left join fetch card.categoryRef
            where collection.collectionType = :collectionType
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )
            """)
    List<CardCollection> findByCollectionTypeForScoring(
            @Param("collectionType") CardCollectionType collectionType
    );

    // ---- Achievement key phrases -------------------------------------------

    @Query("""
            select count(distinct collection.card.id)
            from CardCollection collection
            where collection.user.id = :userId
              and collection.card.categoryRef.id = :categoryId
            """)
    long countDistinctCardsInCategory(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId);

    @Query("""
            select coalesce(max(collection.quantity), 0)
            from CardCollection collection
            where collection.user.id = :userId
            """)
    int findHighestOwnedCopies(@Param("userId") UUID userId);

    /**
     * Le meme compte, mais sur la collection d'une equipe.
     *
     * <p>Le type est un parametre : seules les cartes physiques sont mutualisees,
     * mais un objectif peut porter sur la collection virtuelle, qui elle reste
     * personnelle. Melanger les deux gonflerait un total que le joueur ne voit
     * nulle part.</p>
     */
    @Query("""
            select count(distinct collection.card.id)
            from CardCollection collection
            where collection.user.id in :userIds
              and collection.card.categoryRef.id = :categoryId
              and collection.collectionType = :collectionType
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )
            """)
    long countDistinctCardsInCategoryForUsers(
            @Param("userIds") java.util.Collection<UUID> userIds,
            @Param("categoryId") UUID categoryId,
            @Param("collectionType") fr.insastronaute.webapi.card.domain.CardCollectionType collectionType
    );

    /**
     * Le meme compte, mais sur le type de la carte plutot que sa categorie.
     *
     * <p>Deux axes independants : la categorie porte la rarete, le type porte ce que
     * la carte represente -- un bar, un lieu, une personne. « Avoir toutes les cartes
     * bar » ne se dit pas avec l'un ni avec l'autre seul.</p>
     *
     * <p>Les memes exclusions que partout ailleurs : carte desactivee, carte par
     * defaut, carte bonus. Sans elles, un objectif serait atteint par des cartes que
     * l'ecran du joueur ne compte pas.</p>
     */
    @Query("""
            select count(distinct collection.card.id)
            from CardCollection collection
            where collection.user.id in :userIds
              and collection.card.typeRef.id = :typeId
              and collection.collectionType = :collectionType
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )
            """)
    long countDistinctCardsOfTypeForUsers(
            @Param("userIds") java.util.Collection<UUID> userIds,
            @Param("typeId") UUID typeId,
            @Param("collectionType") fr.insastronaute.webapi.card.domain.CardCollectionType collectionType
    );

    /**
     * Combien de cartes distinctes ces joueurs possedent, toutes categories confondues.
     *
     * <p>Le pendant sans categorie du compte precedent, pour les phrases qui portent
     * sur la collection entiere plutot que sur une rarete.</p>
     */
    @Query("""
            select count(distinct collection.card.id)
            from CardCollection collection
            where collection.user.id in :userIds
              and collection.collectionType = :collectionType
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and (collection.card.categoryRef is null
                   or (collection.card.categoryRef.hidden = false
                       and collection.card.categoryRef.game = false))
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )
              and (:collectionType = fr.insastronaute.webapi.card.domain.CardCollectionType.VIRTUAL
                   or collection.card.categoryRef is null
                   or collection.card.categoryRef.extension = false)
            """)
    long countDistinctCardsForUsers(
            @Param("userIds") java.util.Collection<UUID> userIds,
            @Param("collectionType") fr.insastronaute.webapi.card.domain.CardCollectionType collectionType
    );

    /** Les equipes dont la collection commune atteint le seuil, toutes categories confondues. */
    @Query("""
            select membership.team.id
            from CardCollection collection
            join CardTeamMember membership on membership.user.id = collection.user.id
            where collection.collectionType = fr.insastronaute.webapi.card.domain.CardCollectionType.PHYSICAL
              and (:afterTeamId is null or membership.team.id > :afterTeamId)
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and (collection.card.categoryRef is null
                   or (collection.card.categoryRef.hidden = false
                       and collection.card.categoryRef.game = false))
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )
              and (collection.card.categoryRef is null
                   or collection.card.categoryRef.extension = false)
            group by membership.team.id
            having count(distinct collection.card.id) >= :threshold
            order by membership.team.id asc
            """)
    List<UUID> findTeamsOwningEnoughCards(
            @Param("threshold") long threshold,
            @Param("afterTeamId") UUID afterTeamId,
            Pageable pageable
    );

    @Query("""
            select collection.user.id
            from CardCollection collection
            where collection.collectionType = :collectionType
              and (:afterUserId is null or collection.user.id > :afterUserId)
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and (collection.card.categoryRef is null
                   or (collection.card.categoryRef.hidden = false
                       and collection.card.categoryRef.game = false))
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )
              and (:collectionType = fr.insastronaute.webapi.card.domain.CardCollectionType.VIRTUAL
                   or collection.card.categoryRef is null
                   or collection.card.categoryRef.extension = false)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = collection.user.id
                      and awarded.status = 'VALIDATED'
                )
            group by collection.user.id
            having count(distinct collection.card.id) >= :threshold
            order by collection.user.id asc
            """)
    List<UUID> findUsersOwningEnoughCards(
            @Param("collectionType") fr.insastronaute.webapi.card.domain.CardCollectionType collectionType,
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    /**
     * Les equipes dont la collection commune atteint le seuil.
     *
     * <p>Un joueur peut valider grace a des cartes qu'il n'a pas lui-meme : chercher
     * d'abord les joueurs qui atteignent le seuil seuls, puis remonter a leur equipe, en
     * manquerait precisement ceux que l'equipe porte. La recherche part donc de l'equipe.</p>
     */
    @Query("""
            select membership.team.id
            from CardCollection collection
            join CardTeamMember membership on membership.user.id = collection.user.id
            where collection.card.categoryRef.id = :categoryId
              and collection.collectionType = fr.insastronaute.webapi.card.domain.CardCollectionType.PHYSICAL
              and (:afterTeamId is null or membership.team.id > :afterTeamId)
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )

            group by membership.team.id
            having count(distinct collection.card.id) >= :threshold
            order by membership.team.id asc
            """)
    List<UUID> findTeamsOwningCardsInCategory(
            @Param("categoryId") UUID categoryId,
            @Param("threshold") long threshold,
            @Param("afterTeamId") UUID afterTeamId,
            Pageable pageable
    );

    @Query("""
            select collection.user.id
            from CardCollection collection
            where collection.card.categoryRef.id = :categoryId
              and collection.collectionType = :collectionType
              and (:afterUserId is null or collection.user.id > :afterUserId)
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )

              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = collection.user.id
                      and awarded.status = 'VALIDATED'
                )
            group by collection.user.id
            having count(distinct collection.card.id) >= :threshold
            order by collection.user.id asc
            """)
    List<UUID> findUsersOwningCardsInCategory(
            @Param("categoryId") UUID categoryId,
            @Param("collectionType") fr.insastronaute.webapi.card.domain.CardCollectionType collectionType,
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    /** Les equipes dont la collection commune atteint le seuil sur un type de carte. */
    @Query("""
            select membership.team.id
            from CardCollection collection
            join CardTeamMember membership on membership.user.id = collection.user.id
            where collection.card.typeRef.id = :typeId
              and collection.collectionType = fr.insastronaute.webapi.card.domain.CardCollectionType.PHYSICAL
              and (:afterTeamId is null or membership.team.id > :afterTeamId)
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )

            group by membership.team.id
            having count(distinct collection.card.id) >= :threshold
            order by membership.team.id asc
            """)
    List<UUID> findTeamsOwningCardsOfType(
            @Param("typeId") UUID typeId,
            @Param("threshold") long threshold,
            @Param("afterTeamId") UUID afterTeamId,
            Pageable pageable
    );

    /** Les joueurs qui atteignent seuls le seuil sur un type, et n'ont pas deja recu ce defi. */
    @Query("""
            select collection.user.id
            from CardCollection collection
            where collection.card.typeRef.id = :typeId
              and collection.collectionType = :collectionType
              and (:afterUserId is null or collection.user.id > :afterUserId)
              and collection.card.enabled = true
              and collection.card.defaultCard = false
              and not exists (
                    select 1 from GoofyCardSettings bonus where bonus.card.id = collection.card.id
                )

              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = collection.user.id
                      and awarded.status = 'VALIDATED'
                )
            group by collection.user.id
            having count(distinct collection.card.id) >= :threshold
            order by collection.user.id asc
            """)
    List<UUID> findUsersOwningCardsOfType(
            @Param("typeId") UUID typeId,
            @Param("collectionType") fr.insastronaute.webapi.card.domain.CardCollectionType collectionType,
            @Param("threshold") long threshold,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    /**
     * Le plus grand nombre d'exemplaires d'une meme carte que detient une equipe.
     *
     * <p>Les doubles comptent dans la collection commune -- c'est meme leur seul usage.
     * On somme donc les exemplaires par carte a travers les membres, et on garde le
     * meilleur.</p>
     */
    @Query("""
            select coalesce(max(total), 0)
            from (
                select sum(collection.quantity) as total
                from CardCollection collection
                where collection.user.id in :userIds
                  and collection.collectionType = :collectionType
                group by collection.card.id
            )
            """)
    int findHighestCopiesForUsers(
            @Param("userIds") java.util.Collection<UUID> userIds,
            @Param("collectionType") fr.insastronaute.webapi.card.domain.CardCollectionType collectionType
    );

    /** Les equipes dont une carte atteint ce nombre d'exemplaires, tous membres confondus. */
    @Query("""
            select membership.team.id
            from CardCollection collection
            join CardTeamMember membership on membership.user.id = collection.user.id
            where collection.collectionType = fr.insastronaute.webapi.card.domain.CardCollectionType.PHYSICAL
              and (:afterTeamId is null or membership.team.id > :afterTeamId)
            group by membership.team.id, collection.card.id
            having sum(collection.quantity) >= :threshold
            order by membership.team.id asc
            """)
    List<UUID> findTeamsOwningEnoughCopiesOfOneCard(
            @Param("threshold") int threshold,
            @Param("afterTeamId") UUID afterTeamId,
            Pageable pageable
    );

    @Query("""
            select distinct collection.user.id
            from CardCollection collection
            where collection.quantity >= :threshold
              and collection.collectionType = :collectionType
              and (:afterUserId is null or collection.user.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = collection.user.id
                      and awarded.status = 'VALIDATED'
                )
            order by collection.user.id asc
            """)
    List<UUID> findUsersOwningEnoughCopiesOfOneCard(
            @Param("threshold") int threshold,
            @Param("collectionType") fr.insastronaute.webapi.card.domain.CardCollectionType collectionType,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    /**
     * Combien de ces joueurs detiennent cette carte-ci.
     *
     * <p>Un objectif qui nomme sa carte se mesure a un seul detenteur : au-dela,
     * le compte ne sert plus a rien. Il en faut quand meme un, et non un booleen,
     * parce que la portee peut etre une equipe entiere.</p>
     */
    @Query("""
            select count(distinct collection.user.id)
            from CardCollection collection
            where collection.user.id in :userIds
              and collection.card.id = :cardId
              and collection.collectionType = :collectionType
              and collection.quantity > 0
            """)
    long countHoldersOfCardAmongUsers(
            @Param("userIds") java.util.Collection<UUID> userIds,
            @Param("cardId") UUID cardId,
            @Param("collectionType") CardCollectionType collectionType
    );

    /**
     * Les equipes dont un membre detient cette carte.
     *
     * <p>Seule la collection physique est mutualisee : c'est la seule ou la carte
     * d'un coequipier vaut pour tous.</p>
     */
    @Query("""
            select distinct membership.team.id
            from CardCollection collection
            join CardTeamMember membership on membership.user.id = collection.user.id
            where collection.card.id = :cardId
              and collection.quantity > 0
              and collection.collectionType = fr.insastronaute.webapi.card.domain.CardCollectionType.PHYSICAL
              and (:afterTeamId is null or membership.team.id > :afterTeamId)
            order by membership.team.id asc
            """)
    List<UUID> findTeamsOwningCard(
            @Param("cardId") UUID cardId,
            @Param("afterTeamId") UUID afterTeamId,
            Pageable pageable
    );

    @Query("""
            select distinct collection.user.id
            from CardCollection collection
            where collection.card.id = :cardId
              and collection.quantity > 0
              and collection.collectionType = :collectionType
              and (:afterUserId is null or collection.user.id > :afterUserId)
              and not exists (
                    select 1 from ChallengeSubmission awarded
                    where awarded.challenge.id = :challengeId
                      and awarded.user.id = collection.user.id
                      and awarded.status = 'VALIDATED'
                )
            order by collection.user.id asc
            """)
    List<UUID> findUsersOwningCard(
            @Param("cardId") UUID cardId,
            @Param("collectionType") CardCollectionType collectionType,
            @Param("challengeId") UUID challengeId,
            @Param("afterUserId") UUID afterUserId,
            Pageable pageable
    );

    /**
     * Combien de joueurs detiennent au moins une carte de cette categorie.
     *
     * <p>C'est la portee d'un changement de bareme : ces joueurs voient leur
     * score de collection bouger. Une seule requete, comptee en base - resoudre
     * les detenteurs ligne par ligne serait le N+1 que la specification du
     * registre interdit.</p>
     *
     * <p>Une carte peut porter ses propres points et echapper alors au bareme
     * de sa categorie ; ce compte est donc le nombre de detenteurs de la
     * rarete, pas le sous-ensemble exact que l'override laisse passer. C'est
     * ce qu'un humain compterait en se demandant "ce clic a touche combien de
     * joueurs".</p>
     */
    @Query("""
            select count(distinct collection.user.id)
            from CardCollection collection
            where collection.card.categoryRef.id = :categoryId
              and collection.quantity > 0
            """)
    long countHoldersOfCategory(@Param("categoryId") UUID categoryId);

    /**
     * Combien de joueurs detiennent au moins une carte de ce type de
     * collection.
     *
     * <p>Appele avec {@code PHYSICAL} pour la portee d'un changement des
     * reglages globaux : le multiplicateur physique et les doublons physiques
     * sont les deux reglages qui deplacent un score deja acquis, et ils ne
     * deplacent que le score physique.</p>
     */
    @Query("""
            select count(distinct collection.user.id)
            from CardCollection collection
            where collection.collectionType = :collectionType
              and collection.quantity > 0
            """)
    long countHoldersOfCollectionType(@Param("collectionType") CardCollectionType collectionType);
}
