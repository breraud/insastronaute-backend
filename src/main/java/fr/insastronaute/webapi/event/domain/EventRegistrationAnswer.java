package fr.insastronaute.webapi.event.domain;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * What one registrant answered to one question. Carries exactly one shape: an
 * option for a choice, text for a free field.
 */
@Entity
@Table(
        name = "event_registration_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_registration_answers",
                columnNames = {"registration_id", "question_id"}
        )
)
public class EventRegistrationAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private EventRegistration registration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private EventQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private EventQuestionOption option;

    @Column(name = "text_value", length = 2000)
    private String textValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = TimeTravelContext.instantNow();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeTravelContext.instantNow();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public EventRegistration getRegistration() { return registration; }
    public void setRegistration(EventRegistration registration) { this.registration = registration; }
    public EventQuestion getQuestion() { return question; }
    public void setQuestion(EventQuestion question) { this.question = question; }
    public EventQuestionOption getOption() { return option; }
    public void setOption(EventQuestionOption option) { this.option = option; }
    public String getTextValue() { return textValue; }
    public void setTextValue(String textValue) { this.textValue = textValue; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
