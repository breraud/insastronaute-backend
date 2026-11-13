package fr.insastronaute.webapi.sts.multiplayer.dto;


import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StsPvpBattleDto(
        UUID battleId,
        String status,
        boolean friendly,
        String mode,
        List<List<StsPvpParticipantDto>> teams,
        // Voir StsPvpBattleActionRequest : le JSON libre traverse la frontière HTTP
        // en table, jamais en arbre Jackson 2.
        Map<String, Object> state,
        Map<String, Object> rewards,
        Instant turnDeadline,
        List<UUID> inactiveParticipants,
        Instant serverTime
) {
}
