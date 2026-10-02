package com.example.todo.task;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    /**
     * Фильтр по статусу (один или несколько).
     */
    public static Specification<Task> hasStatusIn(Collection<TaskStatus> statuses) {
        return (root, query, cb) ->
                statuses == null || statuses.isEmpty()
                        ? cb.conjunction()
                        : root.get("status").in(statuses);
    }

    /**
     * Фильтр по приоритету (один или несколько).
     */
    public static Specification<Task> hasPriorityIn(Collection<TaskPriority> priorities) {
        return (root, query, cb) ->
                priorities == null || priorities.isEmpty()
                        ? cb.conjunction()
                        : root.get("priority").in(priorities);
    }

    /**
     * Фильтр по тегу (задача содержит этот тег в @ElementCollection).
     */
    public static Specification<Task> hasTag(String tag) {
        return (root, query, cb) -> {
            if (tag == null || tag.isBlank()) return cb.conjunction();
            var tagsJoin = root.<Task,String>join("tags");       // JOIN task_tags
            return cb.equal(cb.lower(tagsJoin), tag.toLowerCase());
        };
    }

    /**
     * Поиск подстроки в title или description (case-insensitive).
     */
    public static Specification<Task> containsSearch(String term) {
        return (root, query, cb) -> {
            if (term == null || term.isBlank()) return cb.conjunction();
            String pattern = "%" + term.toLowerCase() + "%";
            Predicate inTitle = cb.like(cb.lower(root.get("title")), pattern);
            Predicate inDescription = cb.like(cb.lower(root.get("description")), pattern);
            return cb.or(inTitle, inDescription);
        };
    }

    /**
     * Дедлайн в диапазоне.
     */
    public static Specification<Task> dueDateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), to));
            }
            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Только просроченные: dueDate < today AND status NOT IN (DONE, CANCELLED).
     */
    public static Specification<Task> isOverdue(Boolean overdue) {
        return (root, query, cb) -> {
            if (!Boolean.TRUE.equals(overdue)) return cb.conjunction();
            return cb.and(
                    cb.lessThan(root.get("dueDate"), LocalDate.now()),
                    cb.not(root.get("status").in(TaskStatus.DONE, TaskStatus.CANCELLED))
            );
        };
    }

    /**
     * Комбинирует все фильтры в один Specification.
     */
    public static Specification<Task> buildFilter(
            Collection<TaskStatus> statuses,
            Collection<TaskPriority> priorities,
            String tag,
            String search,
            LocalDate dueFrom,
            LocalDate dueTo,
            Boolean overdue) {
        return Specification
                .where(hasStatusIn(statuses))
                .and(hasPriorityIn(priorities))
                .and(hasTag(tag))
                .and(containsSearch(search))
                .and(dueDateBetween(dueFrom, dueTo))
                .and(isOverdue(overdue));
    }
}