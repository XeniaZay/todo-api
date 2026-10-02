package com.example.todo.task.dto;

import com.example.todo.task.TaskPriority;
import com.example.todo.task.TaskStatus;

import java.time.LocalDate;
import java.util.List;

public record TaskFilterRequest(
        List<TaskStatus> status,
        List<TaskPriority> priority,
        String tag,
        String search,
        LocalDate dueFrom,
        LocalDate dueTo,
        Boolean overdue
) {
}