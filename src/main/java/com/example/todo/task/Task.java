package com.example.todo.task;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "task_tags",
            joinColumns = @JoinColumn(name = "task_id")
    )
    @Column(name = "tag", length = 30, nullable = false)
    private Set<String> tags = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ===== JPA требует no-args конструктор =====
    protected Task() {
    }

    public Task(String title, String description, TaskPriority priority,
                LocalDate dueDate, Set<String> tags) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.dueDate = dueDate;
        if (tags != null) this.tags = new HashSet<>(tags);
    }

    public boolean canTransitionTo(TaskStatus newStatus) {
        if (this.status == newStatus) {
            return false;
        }
        if (this.status == TaskStatus.DONE || this.status == TaskStatus.CANCELLED) {
            return false;
        }
        return switch (this.status) {
            case TODO -> newStatus == TaskStatus.IN_PROGRESS
                    || newStatus == TaskStatus.CANCELLED;
            case IN_PROGRESS -> newStatus == TaskStatus.DONE
                    || newStatus == TaskStatus.TODO
                    || newStatus == TaskStatus.CANCELLED;
            case DONE, CANCELLED -> false;
        };
    }

    // ===== Lifecycle callbacks =====
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ===== Геттеры и сеттеры =====
    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) {
        this.tags = (tags != null) ? new HashSet<>(tags) : new HashSet<>();
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
