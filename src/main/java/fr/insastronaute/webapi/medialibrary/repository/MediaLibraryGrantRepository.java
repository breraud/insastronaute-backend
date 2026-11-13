package fr.insastronaute.webapi.medialibrary.repository;

import fr.insastronaute.webapi.medialibrary.domain.MediaLibraryGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MediaLibraryGrantRepository extends JpaRepository<MediaLibraryGrant, UUID> {

    boolean existsByUser_KeycloakSub(String keycloakSub);

    boolean existsByGrantedBy_Id(UUID userId);

    @Query("select g from MediaLibraryGrant g join fetch g.user join fetch g.grantedBy order by g.grantedAt")
    List<MediaLibraryGrant> findAllWithUsers();
}
