package com.example.todo.task;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TaskMetrics {

    private final MeterRegistry registry;

    // Счётчики
    private final Counter createdCounter;
    private final Counter updatedCounter;
    private final Counter deletedCounter;
    private final Counter statusChangedCounter;
    private final Counter invalidTransitionCounter;
    private final Counter notFoundCounter;

    // Gauge — количество задач по статусам
    private final ConcurrentHashMap<TaskStatus, AtomicLong> tasksByStatus = new ConcurrentHashMap<>();

    // Таймеры
    private final Timer listTimer;
    private final Timer createTimer;

    public TaskMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.createdCounter = Counter.builder("todo.task.created")
                .description("Number of created tasks")
                .register(registry);

        this.updatedCounter = Counter.builder("todo.task.updated")
                .description("Number of updated tasks")
                .register(registry);

        this.deletedCounter = Counter.builder("todo.task.deleted")
                .description("Number of deleted tasks")
                .register(registry);

        this.statusChangedCounter = Counter.builder("todo.task.status.changed")
                .description("Number of status changes")
                .register(registry);

        this.invalidTransitionCounter = Counter.builder("todo.task.status.invalid")
                .description("Number of rejected invalid status transitions")
                .register(registry);

        this.notFoundCounter = Counter.builder("todo.task.not.found")
                .description("Number of task-not-found errors")
                .register(registry);

        // Инициализация Gauge для каждого статуса
        for (TaskStatus status : TaskStatus.values()) {
            AtomicLong holder = new AtomicLong(0);
            tasksByStatus.put(status, holder);
            Gauge.builder("todo.task.count.by.status", holder, AtomicLong::get)
                    .description("Current count of tasks by status")
                    .tag("status", status.name())
                    .register(registry);
        }

        this.listTimer = Timer.builder("todo.task.list.duration")
                .description("Time to list tasks")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.createTimer = Timer.builder("todo.task.create.duration")
                .description("Time to create a task")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    // ===== Счётчики =====
    public void incrementCreated() { createdCounter.increment(); }
    public void incrementUpdated() { updatedCounter.increment(); }
    public void incrementDeleted() { deletedCounter.increment(); }
    public void incrementStatusChanged() { statusChangedCounter.increment(); }
    public void incrementInvalidTransition() { invalidTransitionCounter.increment(); }
    public void incrementNotFound() { notFoundCounter.increment(); }

    // ===== Gauge =====
    public void setTasksCount(TaskStatus status, long count) {
        AtomicLong holder = tasksByStatus.get(status);
        if (holder != null) {
            holder.set(count);
        }
    }

    // ===== Таймеры =====
    public Timer listTimer() { return listTimer; }
    public Timer createTimer() { return createTimer; }
}