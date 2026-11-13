package fr.insastronaute.webapi.challenge.domain;

import fr.insastronaute.webapi.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenge_submission_messages")
public class ChallengeSubmissionMessage {

    public static final String AUTHOR_PLAYER = "PLAYER";
    public static final String AUTHOR_MODERATOR = "MODERATOR";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private ChallengeSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private AppUser author;

    @Column(name = "author_keycloak_sub", nullable = false, length = 255)
    private String authorKeycloakSub;

    @Column(name = "author_display_name", nullable = false, length = 255)
    private String authorDisplayName;

    @Column(name = "author_type", nullable = false, length = 20)
    private String authorType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public ChallengeSubmission getSubmission() { return submission; }
    public void setSubmission(ChallengeSubmission submission) { this.submission = submission; }
    public AppUser getAuthor() { return author; }
    public void setAuthor(AppUser author) { this.author = author; }
    public String getAuthorKeycloakSub() { return authorKeycloakSub; }
    public void setAuthorKeycloakSub(String authorKeycloakSub) { this.authorKeycloakSub = authorKeycloakSub; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public void setAuthorDisplayName(String authorDisplayName) { this.authorDisplayName = authorDisplayName; }
    public String getAuthorType() { return authorType; }
    public void setAuthorType(String authorType) { this.authorType = authorType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }
}
