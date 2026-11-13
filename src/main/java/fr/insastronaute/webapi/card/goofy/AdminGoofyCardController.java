package fr.insastronaute.webapi.card.goofy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/cards/goofy")
@Tag(name = "Admin Cards")
public class AdminGoofyCardController {

    private final GoofyCardService goofyCardService;

    public AdminGoofyCardController(GoofyCardService goofyCardService) {
        this.goofyCardService = goofyCardService;
    }

    @GetMapping
    @Operation(summary = "Read the goofy card settings", description = "Returns the hidden code and the card it grants.")
    public GoofyCardSettingsDto get() {
        return GoofyCardSettingsDto.from(goofyCardService.settings());
    }

    @PutMapping
    @Operation(summary = "Update the goofy card settings", description = "Sets which card the hidden code grants, once per player.")
    public GoofyCardSettingsDto update(@Valid @RequestBody GoofyCardSettingsRequest request) {
        return GoofyCardSettingsDto.from(
                goofyCardService.update(request.cardId(), request.code(), request.enabledOrFalse())
        );
    }

    @PostMapping("/generate-code")
    @Operation(summary = "Generate a unique goofy card code", description = "Generates a fresh 15-character formatted code that does not exist among physical card instances.")
    public GoofyGeneratedCodeDto generateCode() {
        return new GoofyGeneratedCodeDto(goofyCardService.generateUniqueCode());
    }
}
