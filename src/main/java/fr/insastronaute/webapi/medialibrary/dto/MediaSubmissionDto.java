package fr.insastronaute.webapi.medialibrary.dto;

public record MediaSubmissionDto(
        java.util.UUID submissionId,
        java.util.UUID challengeId,
        String challengeTitle,
        String status,
        java.time.Instant submittedAt,
        MediaPersonRefDto author,
        java.util.List<MediaPersonRefDto> participants,
        java.util.List<MediaItemDto> media
) { }
