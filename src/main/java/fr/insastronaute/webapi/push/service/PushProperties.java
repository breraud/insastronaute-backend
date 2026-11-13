package fr.insastronaute.webapi.push.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Le reglage du push.
 *
 * <p>Ferme par defaut. Une paire de cles VAPID identifie ce serveur aupres des services
 * de push des navigateurs ; elle se genere une fois et ne change plus, car changer la
 * cle publique invalide tous les abonnements existants d'un coup.</p>
 */
@ConfigurationProperties(prefix = "app.push")
public class PushProperties {

    private boolean enabled = false;

    /** Un {@code mailto:} ou une URL, que le service de push utilise pour vous joindre. */
    private String subject = "";

    private String vapidPublicKey = "";

    private String vapidPrivateKey = "";

    /** Combien d'envois par passage. Assez pour tenir une diffusion, pas au point de tenir le fil. */
    private int batchSize = 50;

    /** Au-dela, la ligne est abandonnee : le service refuse durablement, insister ne sert plus. */
    private int maxAttempts = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getVapidPublicKey() { return vapidPublicKey; }
    public void setVapidPublicKey(String vapidPublicKey) { this.vapidPublicKey = vapidPublicKey; }

    public String getVapidPrivateKey() { return vapidPrivateKey; }
    public void setVapidPrivateKey(String vapidPrivateKey) { this.vapidPrivateKey = vapidPrivateKey; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public boolean isConfigured() {
        return enabled
                && !subject.isBlank()
                && !vapidPublicKey.isBlank()
                && !vapidPrivateKey.isBlank();
    }
}
