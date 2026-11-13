package fr.insastronaute.webapi.department.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.domain.DepartmentVerificationLink;
import fr.insastronaute.webapi.department.dto.DepartmentVerificationLinkDto;
import fr.insastronaute.webapi.department.dto.DepartmentVerificationLinkUpdateDto;
import fr.insastronaute.webapi.department.dto.DepartmentVerificationLinksUpdateDto;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.department.repository.DepartmentVerificationLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DepartmentVerificationLinkService {

    private final DepartmentVerificationLinkRepository linkRepository;
    private final DepartmentRepository departmentRepository;

    public DepartmentVerificationLinkService(
            DepartmentVerificationLinkRepository linkRepository,
            DepartmentRepository departmentRepository
    ) {
        this.linkRepository = linkRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentVerificationLinkDto> findAll() {
        return linkRepository.findAllWithDepartments().stream()
                .map(DepartmentVerificationLinkDto::from)
                .toList();
    }

    @Transactional
    public List<DepartmentVerificationLinkDto> replaceAll(DepartmentVerificationLinksUpdateDto dto) {
        List<DepartmentVerificationLinkUpdateDto> requestedLinks = dto.links();
        Set<LinkKey> linkKeys = new LinkedHashSet<>();
        Set<UUID> departmentIds = new HashSet<>();

        for (DepartmentVerificationLinkUpdateDto link : requestedLinks) {
            UUID verifyingDepartmentId = link.verifyingDepartmentId();
            UUID verifiedDepartmentId = link.verifiedDepartmentId();

            if (verifyingDepartmentId.equals(verifiedDepartmentId)) {
                throw new BadRequestException(
                        "Un département ne peut pas vérifier ses propres preuves.",
                        "links",
                        "Les départements vérificateur et vérifié doivent être différents."
                );
            }

            linkKeys.add(new LinkKey(verifyingDepartmentId, verifiedDepartmentId));
            departmentIds.add(verifyingDepartmentId);
            departmentIds.add(verifiedDepartmentId);
        }

        Map<UUID, Department> departments = departmentRepository.findAllById(departmentIds).stream()
                .collect(Collectors.toMap(Department::getId, Function.identity()));

        if (departments.size() != departmentIds.size()) {
            throw new BadRequestException(
                    "Un ou plusieurs départements sont introuvables.",
                    "links",
                    "Tous les identifiants de département doivent exister."
            );
        }

        linkRepository.deleteAllInBatch();

        List<DepartmentVerificationLink> links = linkKeys.stream()
                .map(key -> {
                    DepartmentVerificationLink link = new DepartmentVerificationLink();
                    link.setVerifyingDepartment(departments.get(key.verifyingDepartmentId()));
                    link.setVerifiedDepartment(departments.get(key.verifiedDepartmentId()));
                    return link;
                })
                .toList();

        if (!links.isEmpty()) {
            linkRepository.saveAll(links);
        }

        return findAll();
    }

    private record LinkKey(UUID verifyingDepartmentId, UUID verifiedDepartmentId) {
    }
}
