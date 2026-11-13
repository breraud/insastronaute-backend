package fr.insastronaute.webapi.medialibrary.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MediaExportDto(UUID exportId, Instant createdAt, MediaPersonRefDto createdBy, int itemCount,
                             long totalBytes, int partCount, List<MediaExportPartDto> parts) { }
