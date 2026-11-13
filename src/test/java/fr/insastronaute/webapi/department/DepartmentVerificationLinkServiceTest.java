package fr.insastronaute.webapi.department;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.domain.DepartmentVerificationLink;
import fr.insastronaute.webapi.department.dto.DepartmentVerificationLinkUpdateDto;
import fr.insastronaute.webapi.department.dto.DepartmentVerificationLinksUpdateDto;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import fr.insastronaute.webapi.department.repository.DepartmentVerificationLinkRepository;
import fr.insastronaute.webapi.department.service.DepartmentVerificationLinkService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

class DepartmentVerificationLinkServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void replaceAllStoresBothDirectionsWhenRequested() {
        DepartmentVerificationLinkRepository linkRepository = mock(DepartmentVerificationLinkRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UUID itiId = UUID.randomUUID();
        UUID gmId = UUID.randomUUID();
        Department iti = department(itiId, "ITI");
        Department gm = department(gmId, "GM");

        when(departmentRepository.findAllById(anyIterable())).thenReturn(List.of(iti, gm));
        when(linkRepository.findAllWithDepartments()).thenReturn(List.of());

        DepartmentVerificationLinkService service = new DepartmentVerificationLinkService(
                linkRepository,
                departmentRepository
        );

        service.replaceAll(new DepartmentVerificationLinksUpdateDto(List.of(
                new DepartmentVerificationLinkUpdateDto(itiId, gmId),
                new DepartmentVerificationLinkUpdateDto(gmId, itiId)
        )));

        ArgumentCaptor<Iterable<DepartmentVerificationLink>> linksCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(linkRepository).deleteAllInBatch();
        verify(linkRepository).saveAll(linksCaptor.capture());

        assertThat(linksCaptor.getValue())
                .extracting(link -> link.getVerifyingDepartment().getId() + "->" + link.getVerifiedDepartment().getId())
                .containsExactlyInAnyOrder(itiId + "->" + gmId, gmId + "->" + itiId);
    }

    @Test
    void replaceAllRejectsSelfVerificationLinks() {
        DepartmentVerificationLinkService service = new DepartmentVerificationLinkService(
                mock(DepartmentVerificationLinkRepository.class),
                mock(DepartmentRepository.class)
        );
        UUID departmentId = UUID.randomUUID();

        assertThatThrownBy(() -> service.replaceAll(new DepartmentVerificationLinksUpdateDto(List.of(
                new DepartmentVerificationLinkUpdateDto(departmentId, departmentId)
        ))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Un département ne peut pas vérifier ses propres preuves.");
    }

    @Test
    void replaceAllRejectsUnknownDepartments() {
        DepartmentVerificationLinkRepository linkRepository = mock(DepartmentVerificationLinkRepository.class);
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UUID itiId = UUID.randomUUID();
        UUID missingDepartmentId = UUID.randomUUID();

        when(departmentRepository.findAllById(anyIterable())).thenReturn(List.of(department(itiId, "ITI")));

        DepartmentVerificationLinkService service = new DepartmentVerificationLinkService(
                linkRepository,
                departmentRepository
        );

        assertThatThrownBy(() -> service.replaceAll(new DepartmentVerificationLinksUpdateDto(List.of(
                new DepartmentVerificationLinkUpdateDto(itiId, missingDepartmentId)
        ))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Un ou plusieurs départements sont introuvables.");

        verify(linkRepository, never()).deleteAllInBatch();
    }

    private static Department department(UUID id, String shortName) {
        Department department = new Department();
        department.setId(id);
        department.setName(shortName);
        department.setShortName(shortName);
        return department;
    }
}
