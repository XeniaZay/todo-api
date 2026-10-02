package com.example.todo.task;

import com.example.todo.task.dto.TaskFilterRequest;
import com.example.todo.task.dto.TaskPageResponse;
import com.example.todo.task.dto.TaskRequest;
import com.example.todo.task.dto.TaskResponse;
import com.example.todo.task.exception.InvalidStatusTransitionException;
import com.example.todo.task.exception.TaskNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository repository;
    private final TaskMetrics metrics;

    public TaskService(TaskRepository repository, TaskMetrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    @jakarta.annotation.PostConstruct
    @Transactional(readOnly = true)
    public void initMetrics() {
        refreshCounts();
        log.info("Task metrics initialized");
    }

    // ============================================================
    // CREATE
    // ============================================================
    @Transactional
    public TaskResponse create(TaskRequest request) {
        Task task = new Task(
                request.title(),
                request.description(),
                request.priority(),
                request.dueDate(),
                request.tags()
        );
        Task saved = repository.save(task);

        metrics.incrementCreated();
        refreshCounts();

        log.info("Task created id={} title={} priority={}",
                saved.getId(), saved.getTitle(), saved.getPriority());

        return toResponse(saved);
    }

    // ============================================================
    // READ — один
    // ============================================================
    @Transactional(readOnly = true)
    public TaskResponse getById(Long id) {
        Task task = repository.findById(id)
                .orElseThrow(() ->{
                    metrics.incrementNotFound();
                    return new TaskNotFoundException(id);
                });
        return toResponse(task);
    }

    // ============================================================
    // READ — список
    // ============================================================
    @Transactional(readOnly = true)
    public TaskPageResponse list(TaskFilterRequest filter, int page, int size, String sortBy, String sortDir) {
        return metrics.listTimer().record(() -> {
            Sort sort = sortDir.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();

            Specification<Task> spec = TaskSpecifications.buildFilter(
                    filter.status(),
                    filter.priority(),
                    filter.tag(),
                    filter.search(),
                    filter.dueFrom(),
                    filter.dueTo(),
                    filter.overdue()
            );

            Page<Task> taskPage = repository.findAll(spec, PageRequest.of(page, size, sort));

            return new TaskPageResponse(
                    taskPage.getContent().stream().map(this::toResponse).toList(),
                    taskPage.getNumber(),
                    taskPage.getSize(),
                    taskPage.getTotalElements(),
                    taskPage.getTotalPages()
            );
        });
    }

    // ============================================================
    // UPDATE — частичное
    // ============================================================
    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = repository.findById(id)
                .orElseThrow(() -> {
                    metrics.incrementNotFound();
                    return new TaskNotFoundException(id);
                });

        if (request.title() != null) task.setTitle(request.title());
        if (request.description() != null) task.setDescription(request.description());
        if (request.priority() != null) task.setPriority(request.priority());
        if (request.dueDate() != null) task.setDueDate(request.dueDate());
        if (request.tags() != null) task.setTags(request.tags());

        Task saved = repository.save(task);

        metrics.incrementUpdated();

        log.info("Task updated id={} title={}", saved.getId(), saved.getTitle());

        return toResponse(saved);
    }

    // ============================================================
    // PATCH — смена статуса
    // ============================================================
    @Transactional
    public TaskResponse changeStatus(Long id, TaskStatus newStatus) {
        Task task = repository.findById(id)
                .orElseThrow(() -> {
                    metrics.incrementNotFound();
                    return new TaskNotFoundException(id);
                });

        if (!task.canTransitionTo(newStatus)) {
            metrics.incrementInvalidTransition();
            throw new InvalidStatusTransitionException(task.getStatus(), newStatus);
        }

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);
        metrics.incrementStatusChanged();
        refreshCounts();

        log.info("Task status changed id={} from={} to={}",
                task.getId(), oldStatus, newStatus);

        return toResponse(task);
    }

    // ============================================================
    // DELETE
    // ============================================================
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            metrics.incrementNotFound();
            throw new TaskNotFoundException(id);
        }
        repository.deleteById(id);
        metrics.incrementDeleted();
        refreshCounts();
        log.info("Task deleted id={}", id);
    }

    // ============================================================
    // Маппинг Entity → DTO
    // ============================================================
    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getStatus(),
                task.getDueDate(),
                task.getTags(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private void refreshCounts() {
        for (TaskStatus status : TaskStatus.values()) {
            long count = repository.count(
                    (root, query, cb) -> cb.equal(root.get("status"), status));
            metrics.setTasksCount(status, count);
        }
    }



}
