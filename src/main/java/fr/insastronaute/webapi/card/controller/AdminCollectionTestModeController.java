package fr.insastronaute.webapi.card.controller;

import fr.insastronaute.webapi.card.dto.CollectionTestModeDto;
import fr.insastronaute.webapi.card.dto.CollectionTestModeRequest;
import fr.insastronaute.webapi.card.service.CollectionTestModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cards/collection-test-mode")
@Tag(name = "Admin Cards")
public class AdminCollectionTestModeController {

    private final CollectionTestModeService testMode;

    public AdminCollectionTestModeController(CollectionTestModeService testMode) {
        this.testMode = testMode;
    }

    @GetMapping
    @Operation(summary = "Read the collection test mode")
    public CollectionTestModeDto getMode() {
        return new CollectionTestModeDto(testMode.allCardsOwned());
    }

    @PutMapping
    @Operation(
            summary = "Treat every collection as complete",
            description = "Test switch: every player sees and uses the whole catalogue, so what cards unlock can be "
                    + "tried without distributing them. Nothing is granted — real collections still decide the "
                    + "leaderboard, the achievements and the collection points."
    )
    public CollectionTestModeDto setMode(@Valid @RequestBody CollectionTestModeRequest request) {
        return new CollectionTestModeDto(testMode.setAllCardsOwned(Boolean.TRUE.equals(request.allCardsOwned())));
    }
}
