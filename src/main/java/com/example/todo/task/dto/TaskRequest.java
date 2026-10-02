package com.example.todo.task.dto;

import com.example.todo.common.validation.Create;
import com.example.todo.common.validation.Update;
import com.example.todo.task.TaskPriority;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Set;

public record TaskRequest(@NotBlank(groups = {Create.class}, message = "title must not be blank")
                          @Size(groups = { Create.class, Update.class }, min = 1, max = 200, message = "title must be 1-200 characters")
                          String title,

                          @Size(groups = { Create.class, Update.class },max = 2000, message = "description must not exceed 2000 characters")
                          String description,

                          @NotNull(groups = { Create.class },message = "priority is required")
                          TaskPriority priority,

                          @FutureOrPresent(groups = { Create.class, Update.class },message = "dueDate must be today or in the future")
                          LocalDate dueDate,

                          @Size(groups = { Create.class, Update.class },max = 20, message = "at most 20 tags allowed")
                          Set<@NotBlank(groups = { Create.class },message = "tag must not be blank")
                          @Size(groups = { Create.class, Update.class },max = 30, message = "tag must not exceed 30 characters") String> tags) {
}
