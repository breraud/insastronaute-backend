package fr.insastronaute.webapi.sts.dto;

import jakarta.validation.constraints.NotBlank;

public record StsStartRunRequest(
        @NotBlank String character,
        String clientVersion
) {
}
