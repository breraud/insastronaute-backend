package fr.insastronaute.webapi.card.repository;

import fr.insastronaute.webapi.card.domain.CardInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardInstanceRepository extends JpaRepository<CardInstance, UUID>, CardInstanceRepositoryCustom {

    boolean existsByPublicId(String publicId);

    boolean existsByPublicIdAndIdNot(String publicId, UUID id);

    List<CardInstance> findByCardIdOrderByCreatedAtDesc(UUID cardId);

    long countByCardId(UUID cardId);

    List<CardInstance> findByOwnerIdAndCardIdOrderByAssignedAtAsc(UUID ownerId, UUID cardId);

    long countByOwnerIdAndCardId(UUID ownerId, UUID cardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select instance
            from CardInstance instance
            where instance.id = :id
            """)
    Optional<CardInstance> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select instance
            from CardInstance instance
            where instance.publicId = :publicId
            """)
    Optional<CardInstance> findByPublicIdForUpdate(@Param("publicId") String publicId);
}
