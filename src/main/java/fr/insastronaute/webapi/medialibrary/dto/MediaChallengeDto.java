package fr.insastronaute.webapi.medialibrary.dto;

public record MediaChallengeDto(java.util.UUID challengeId, String title, String category,
                                long submissionCount, long retainedCount) { }
