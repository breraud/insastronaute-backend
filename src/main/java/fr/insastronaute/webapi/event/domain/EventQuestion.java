package fr.insastronaute.webapi.event.domain;

import fr.insastronaute.webapi.common.timetravel.TimeTravelContext;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One question asked of everyone registering for an event. */
@Entity
@Table(name = "event_questions")
public class EventQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 300)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventQuestionType type = EventQuestionType.SINGLE_CHOICE;

    @Column(nullable = false)
    private Boolean required = false;

    @Column(nullable = false)
    private Integer position = 0;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<EventQuestionOption> options = new ArrayList<>();

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
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public EventQuestionType getType() { return type; }
    public void setType(EventQuestionType type) { this.type = type == null ? EventQuestionType.SINGLE_CHOICE : type; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required != null && required; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position == null ? 0 : position; }
    public List<EventQuestionOption> getOptions() { return options; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
