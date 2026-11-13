package fr.insastronaute.webapi.department.controller;

import fr.insastronaute.webapi.department.dto.DepartmentVerificationLinkDto;
import fr.insastronaute.webapi.department.dto.DepartmentVerificationLinksUpdateDto;
import fr.insastronaute.webapi.department.service.DepartmentVerificationLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/department-verification-links")
@Tag(name = "Admin Departments")
public class AdminDepartmentVerificationLinkController {

    private final DepartmentVerificationLinkService service;

    public AdminDepartmentVerificationLinkController(DepartmentVerificationLinkService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List department verification links", description = "Returns moderation verification links configured for departments.")
    public List<DepartmentVerificationLinkDto> getLinks() {
        return service.findAll();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace department verification links", description = "Replaces the full set of department moderation verification links.")
    public List<DepartmentVerificationLinkDto> replaceLinks(
            @Valid @RequestBody DepartmentVerificationLinksUpdateDto dto
    ) {
        return service.replaceAll(dto);
    }
}
