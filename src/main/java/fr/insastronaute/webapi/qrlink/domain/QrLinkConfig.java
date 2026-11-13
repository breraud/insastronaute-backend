package fr.insastronaute.webapi.qrlink.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "qr_link_config")
public class QrLinkConfig {

    public static final Short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "redirect_url", nullable = false, columnDefinition = "TEXT")
    private String redirectUrl;

    @Column(name = "challenge_mode_enabled", nullable = false)
    private Boolean challengeModeEnabled = false;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }
    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }
    public Boolean getChallengeModeEnabled() { return challengeModeEnabled; }
    public void setChallengeModeEnabled(Boolean challengeModeEnabled) {
        this.challengeModeEnabled = challengeModeEnabled != null && challengeModeEnabled;
    }
}
