package com.example.todo.task.dto;

import com.example.todo.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(

        @NotNull(message = "status is required")
        TaskStatus status
) {
}