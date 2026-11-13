package fr.insastronaute.webapi.department.controller;

import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.dto.DepartmentColorUpdateDto;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/departments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDepartmentController {
    private final DepartmentRepository departmentRepository;

    public AdminDepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @PutMapping("/{id}/color")
    public Department updateColor(@PathVariable UUID id, @Valid @RequestBody DepartmentColorUpdateDto dto) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Departement introuvable."));
        department.setColor(dto.color().toUpperCase());
        return departmentRepository.save(department);
    }
}
