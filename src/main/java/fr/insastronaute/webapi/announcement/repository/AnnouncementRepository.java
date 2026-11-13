package fr.insastronaute.webapi.announcement.repository;

import fr.insastronaute.webapi.announcement.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    List<Announcement> findAllByOrderByCreatedAtDesc();

    /**
     * Les annonces a montrer a ce joueur : dans leur fenetre, actives, et qu'il n'a
     * pas deja refermees.
     *
     * <p>Le tri met les plus recentes d'abord : c'est l'ordre dans lequel on veut
     * les lire quand plusieurs attendent.</p>
     */
    @Query("""
            select announcement from Announcement announcement
            where announcement.active = true
              and (announcement.startDate is null or announcement.startDate <= :now)
              and (announcement.endDate is null or announcement.endDate >= :now)
              and not exists (
                    select 1 from AnnouncementDismissal dismissal
                    where dismissal.key.announcementId = announcement.id
                      and dismissal.key.userId = :userId
                )
            order by announcement.createdAt desc
            """)
    List<Announcement> findVisibleFor(@Param("userId") UUID userId, @Param("now") Instant now);
}
