package fr.insastronaute.webapi.article.controller;

import fr.insastronaute.webapi.article.dto.CategoryDto;
import fr.insastronaute.webapi.article.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List article categories", description = "Returns the categories available for article assignment.")
    public List<CategoryDto> listCategories() {
        return categoryService.listCategories();
    }
}
