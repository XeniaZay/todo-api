package com.example.todo.task.dto;

import com.example.todo.task.TaskPriority;
import com.example.todo.task.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskPriority priority,
        TaskStatus status,
        LocalDate dueDate,
        Set<String> tags,
        Instant createdAt,
        Instant updatedAt
) {
}