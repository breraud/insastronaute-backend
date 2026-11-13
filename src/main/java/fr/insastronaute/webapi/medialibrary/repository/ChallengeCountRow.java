package fr.insastronaute.webapi.medialibrary.repository;

import java.util.UUID;

public record ChallengeCountRow(UUID challengeId, String title, String category, Long submissionCount) {
}
