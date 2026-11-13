package fr.insastronaute.webapi.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "event_question_options")
public class EventQuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private EventQuestion question;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(nullable = false)
    private Integer position = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public EventQuestion getQuestion() { return question; }
    public void setQuestion(EventQuestion question) { this.question = question; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position == null ? 0 : position; }
}
