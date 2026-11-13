package fr.insastronaute.webapi.game.dto;

import java.time.Instant;
import java.util.List;

public record GameConfigReleaseDto(
        String version,
        String objectPrefix,
        List<String> files,
        Instant releasedAt
) {
}
