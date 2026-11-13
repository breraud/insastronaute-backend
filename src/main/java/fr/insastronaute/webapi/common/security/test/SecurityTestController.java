package fr.insastronaute.webapi.common.security.test;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sondes de la chaine de filtres : elles ne portent aucune logique metier et
 * n'existent que pour verifier, depuis les tests, qu'un role donne franchit ou
 * non un matcher.
 *
 * <p><strong>Confinees au profil test.</strong> Livrees en production elles
 * offrent a un attaquant un oracle gratuit sur le decoupage des roles : trois
 * requetes suffisent a savoir si une session est simple joueur, moderateur ou
 * administrateur, sans toucher a un seul endpoint metier.</p>
 */
@Profile("test")
@RestController
public class SecurityTestController {
    @GetMapping("/api/test/user")
    public Map<String, String> user() {
        return Map.of("status", "connected user ok");
    }

    @GetMapping("/api/moderation/test")
    public Map<String, String> moderator() {
        return Map.of("status", "moderator ok");
    }

    @GetMapping("/api/moderation/history/test")
    public Map<String, String> moderationHistory() {
        return Map.of("status", "moderation history ok");
    }

    @GetMapping("/api/admin/test")
    public Map<String, String> admin() {
        return Map.of("status", "admin ok");
    }
}
