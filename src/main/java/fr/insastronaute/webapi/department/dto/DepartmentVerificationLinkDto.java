package fr.insastronaute.webapi.department.dto;

import fr.insastronaute.webapi.department.domain.DepartmentVerificationLink;

import java.util.UUID;

public record DepartmentVerificationLinkDto(
        UUID verifyingDepartmentId,
        String verifyingDepartmentShortName,
        String verifyingDepartmentName,
        UUID verifiedDepartmentId,
        String verifiedDepartmentShortName,
        String verifiedDepartmentName
) {
    public static DepartmentVerificationLinkDto from(DepartmentVerificationLink link) {
        return new DepartmentVerificationLinkDto(
                link.getVerifyingDepartment().getId(),
                link.getVerifyingDepartment().getShortName(),
                link.getVerifyingDepartment().getName(),
                link.getVerifiedDepartment().getId(),
                link.getVerifiedDepartment().getShortName(),
                link.getVerifiedDepartment().getName()
        );
    }
}
