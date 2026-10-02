package com.example.todo.task.dto;

import java.util.List;

public record TaskPageResponse(
        List<TaskResponse> content,
        int page,
        int size,
        long total,
        int totalPages
) {
}