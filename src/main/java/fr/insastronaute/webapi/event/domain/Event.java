package fr.insastronaute.webapi.event.domain;

import fr.insastronaute.webapi.article.domain.Article;
import fr.insastronaute.webapi.department.domain.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(name = "short_description", nullable = false, length = 500)
    private String shortDescription;

    @Column(length = 255)
    private String location;

    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;

    @Column(name = "cover_image_object_key", length = 512)
    private String coverImageObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventSource source = EventSource.GLOBAL;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "registration_enabled", nullable = false)
    private Boolean registrationEnabled = false;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "waitlist_enabled", nullable = false)
    private Boolean waitlistEnabled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    private Article article;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "event_categories_join",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<EventCategory> categories = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "event_target_departments",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    private Set<Department> targetDepartments = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = fr.insastronaute.webapi.common.timetravel.TimeTravelContext.instantNow();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getCoverImageObjectKey() {
        return coverImageObjectKey;
    }

    public void setCoverImageObjectKey(String coverImageObjectKey) {
        this.coverImageObjectKey = coverImageObjectKey;
    }

    public EventSource getSource() {
        return source;
    }

    public void setSource(EventSource source) {
        this.source = source == null ? EventSource.GLOBAL : source;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Boolean getRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(Boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled != null && registrationEnabled;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Boolean getWaitlistEnabled() {
        return waitlistEnabled;
    }

    public void setWaitlistEnabled(Boolean waitlistEnabled) {
        this.waitlistEnabled = waitlistEnabled != null && waitlistEnabled;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public Set<EventCategory> getCategories() {
        return categories;
    }

    public void setCategories(Set<EventCategory> categories) {
        this.categories = categories == null ? new HashSet<>() : new HashSet<>(categories);
    }

    public Set<Department> getTargetDepartments() {
        return targetDepartments;
    }

    public void setTargetDepartments(Set<Department> targetDepartments) {
        this.targetDepartments = targetDepartments == null ? new HashSet<>() : new HashSet<>(targetDepartments);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
