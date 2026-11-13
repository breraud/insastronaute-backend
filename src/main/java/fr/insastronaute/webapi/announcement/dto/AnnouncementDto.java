package fr.insastronaute.webapi.announcement.dto;

import fr.insastronaute.webapi.announcement.domain.Announcement;
import fr.insastronaute.webapi.announcement.domain.AnnouncementKind;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementDto(
        UUID id,
        String title,
        String body,
        AnnouncementKind kind,
        boolean active,
        Instant startDate,
        Instant endDate,
        /** L'onglet vers lequel elle renvoie, ou {@code null}. */
        String link,
        Instant createdAt
) {
    public static AnnouncementDto from(Announcement announcement) {
        return new AnnouncementDto(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getKind(),
                announcement.isActive(),
                announcement.getStartDate(),
                announcement.getEndDate(),
                announcement.getLink(),
                announcement.getCreatedAt()
        );
    }
}
