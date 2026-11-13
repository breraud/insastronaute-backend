package fr.insastronaute.webapi.department.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
        name = "department_verification_links",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_department_verification_links_departments",
                columnNames = {"verifying_department_id", "verified_department_id"}
        )
)
public class DepartmentVerificationLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verifying_department_id", nullable = false)
    private Department verifyingDepartment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verified_department_id", nullable = false)
    private Department verifiedDepartment;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Department getVerifyingDepartment() { return verifyingDepartment; }
    public void setVerifyingDepartment(Department verifyingDepartment) {
        this.verifyingDepartment = verifyingDepartment;
    }

    public Department getVerifiedDepartment() { return verifiedDepartment; }
    public void setVerifiedDepartment(Department verifiedDepartment) {
        this.verifiedDepartment = verifiedDepartment;
    }
}
