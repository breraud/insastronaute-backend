package fr.insastronaute.webapi.sts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StsContinueAfterRetreatRequest(
        @NotNull @PositiveOrZero Integer expectedAct
) {
}
