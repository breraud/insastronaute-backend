package fr.insastronaute.webapi.medialibrary.repository;

import java.util.UUID;

public record PersonCountRow(UUID userId, Long submissionCount) {
}
