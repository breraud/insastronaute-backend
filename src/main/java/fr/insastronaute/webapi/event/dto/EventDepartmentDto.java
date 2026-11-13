package fr.insastronaute.webapi.event.dto;

import java.util.UUID;

public record EventDepartmentDto(
        UUID id,
        String name,
        String shortName
) {
}
