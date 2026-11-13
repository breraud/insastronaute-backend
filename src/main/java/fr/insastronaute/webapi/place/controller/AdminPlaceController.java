package fr.insastronaute.webapi.place.controller;

import fr.insastronaute.webapi.place.dto.PlaceConfigDto;
import fr.insastronaute.webapi.place.dto.PlaceConfigUpdateDto;
import fr.insastronaute.webapi.place.service.PlaceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/place/config")
@Tag(name = "Admin Place")
public class AdminPlaceController {

    private final PlaceConfigService configService;

    public AdminPlaceController(PlaceConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @Operation(summary = "Get r/place configuration", description = "Returns the dynamic event window, canvas, and cooldown settings.")
    public PlaceConfigDto getConfig() {
        return configService.getConfig();
    }

    @PutMapping
    @Operation(summary = "Update r/place configuration", description = "Replaces the dynamic event window, resizes the canvas, and updates cooldown settings.")
    public PlaceConfigDto updateConfig(@Valid @RequestBody PlaceConfigUpdateDto update) {
        return configService.updateConfig(update);
    }
}
