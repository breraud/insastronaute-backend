package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.CardInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CardInstanceRepositoryCustom {

    Page<CardInstance> searchAdmin(
            String search,
            UUID cardId,
            String publicId,
            UUID ownerUserId,
            String type,
            Integer number,
            String assignmentStatus,
            Pageable pageable
    );

    List<CardInstance> searchAdmin(UUID cardId, String publicId, UUID ownerUserId, String type, Integer number);
}
