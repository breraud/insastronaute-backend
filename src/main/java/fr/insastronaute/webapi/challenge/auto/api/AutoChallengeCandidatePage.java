package fr.insastronaute.webapi.challenge.auto.api;

import java.util.List;
import java.util.UUID;

public record AutoChallengeCandidatePage(List<UUID> userIds, UUID nextCursor) {
}
