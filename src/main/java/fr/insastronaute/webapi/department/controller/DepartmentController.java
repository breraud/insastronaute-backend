package fr.insastronaute.webapi.department.controller;

import fr.insastronaute.webapi.department.domain.Department;
import fr.insastronaute.webapi.department.repository.DepartmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@Tag(name = "Departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * Les filieres, et seulement elles, sauf demande contraire.
     *
     * <p>PERF est un departement virtuel : une audience pour le ciblage des evenements,
     * le departement de personne. Le rendre par defaut le ferait apparaitre dans Place,
     * dans les cibles de defis, dans les legendes et dans les effectifs, c'est-a-dire
     * partout ou il ne veut rien dire. Le seul appelant qui le demande est le formulaire
     * d'evenement.</p>
     */
    @GetMapping
    @Operation(
            summary = "List departments",
            description = "Returns the departments known by the application. Virtual departments -- "
                    + "audiences such as PERF, which nobody belongs to -- are left out unless "
                    + "includeVirtual is true."
    )
    public List<Department> getAllDepartments(
            @RequestParam(required = false, defaultValue = "false") boolean includeVirtual
    ) {
        return includeVirtual ? departmentRepository.findAll() : departmentRepository.findAllByVirtualFalse();
    }
}
