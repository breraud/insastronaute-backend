package fr.insastronaute.webapi.auth.gate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.auth-gate")
public class AuthGateProperties {

    private boolean enabled = true;
    private int admissionsPerSecond = 15;
    private int maxActiveInFlight = 50;
    private Duration tokenTtl = Duration.ofSeconds(60);
    private Duration ticketTtl = Duration.ofMinutes(10);
    private String secret = "insastronaute-auth-gate-hmac-sha256-default-secret-key-32bytes";

    /**
     * Le code qui fait sauter la file.
     *
     * Il n'authentifie personne : le franchir, c'est seulement arriver devant Keycloak, qui
     * decide seul. Il protege le portail d'un afflux, il n'ouvre aucune porte. Vide -- la
     * valeur par defaut -- le raccourci n'existe pas.
     */
    private String fastPassCode = "";

    public String getFastPassCode() {
        return fastPassCode;
    }

    public void setFastPassCode(String fastPassCode) {
        this.fastPassCode = fastPassCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAdmissionsPerSecond() {
        return admissionsPerSecond;
    }

    public void setAdmissionsPerSecond(int admissionsPerSecond) {
        this.admissionsPerSecond = admissionsPerSecond;
    }

    public int getMaxActiveInFlight() {
        return maxActiveInFlight;
    }

    public void setMaxActiveInFlight(int maxActiveInFlight) {
        this.maxActiveInFlight = maxActiveInFlight;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public Duration getTicketTtl() {
        return ticketTtl;
    }

    public void setTicketTtl(Duration ticketTtl) {
        this.ticketTtl = ticketTtl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
