package fr.insastronaute.webapi.announcement.repository;

import fr.insastronaute.webapi.announcement.domain.AnnouncementDismissal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementDismissalRepository
        extends JpaRepository<AnnouncementDismissal, AnnouncementDismissal.Key> {
}
