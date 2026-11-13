package fr.insastronaute.webapi.department.repository;

import fr.insastronaute.webapi.department.domain.DepartmentVerificationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentVerificationLinkRepository extends JpaRepository<DepartmentVerificationLink, UUID> {

    boolean existsByVerifyingDepartmentIdAndVerifiedDepartmentId(UUID verifyingDepartmentId, UUID verifiedDepartmentId);

    @Query("""
            select link
            from DepartmentVerificationLink link
            join fetch link.verifyingDepartment verifyingDepartment
            join fetch link.verifiedDepartment verifiedDepartment
            order by verifyingDepartment.shortName, verifiedDepartment.shortName
            """)
    List<DepartmentVerificationLink> findAllWithDepartments();
}
