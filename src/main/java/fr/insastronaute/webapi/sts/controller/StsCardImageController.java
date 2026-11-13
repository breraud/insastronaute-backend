package fr.insastronaute.webapi.sts.controller;

import fr.insastronaute.webapi.card.dto.CardDto;
import fr.insastronaute.webapi.card.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sts")
@Tag(name = "STS Catalog")
public class StsCardImageController {

    private final CardService cardService;

    public StsCardImageController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/cardsImage")
    @Operation(
            summary = "List STS card images",
            description = "Returns every non-cover card required for STS artwork, including cards from hidden categories."
    )
    public List<CardDto> cardsImage() {
        return cardService.findAllForSts();
    }
}
