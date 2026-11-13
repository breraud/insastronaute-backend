package fr.insastronaute.webapi.sts.combat.api;

import fr.insastronaute.webapi.sts.combat.api.dto.CombatMessageEnvelope;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class CombatEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public CombatEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishEvent(String userId, String combatId, CombatMessageEnvelope envelope) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/sts/combats/" + combatId, envelope);
    }
}
