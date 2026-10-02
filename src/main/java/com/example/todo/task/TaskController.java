package com.example.todo.task;

import com.example.todo.common.validation.Create;
import com.example.todo.common.validation.Update;
import com.example.todo.task.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final String SORT_BY_PATTERN =
            "^(id|title|priority|status|dueDate|createdAt|updatedAt)$";

    TaskService service;

    public TaskController(TaskService service){
        this.service = service;
    }

    @GetMapping(value = "/{id}",
            produces = "application/json")
    ResponseEntity<TaskResponse> getById(@PathVariable Long id){
        TaskResponse response = service.getById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping(produces = "application/json")
    ResponseEntity<TaskPageResponse> list(@RequestParam(defaultValue = "0")
                                          @Min(value = 0, message = "page must be >= 0")
                                          int page,

                                          @RequestParam(defaultValue = "20")
                                          @Min(value = 1, message = "size must be between 1 and 100")
                                          @Max(value = 100, message = "size must be between 1 and 100")
                                          int size,

                                          @RequestParam(defaultValue = "id")
                                          @Pattern(regexp = SORT_BY_PATTERN,
                                                  message = "sortBy must be one of: id, title, priority, status, dueDate, createdAt, updatedAt")
                                          String sortBy,

                                          @RequestParam(defaultValue = "asc")
                                          @Pattern(regexp = "^(asc|desc)$", message = "sortDir must be 'asc' or 'desc'")
                                          String sortDir,
                                          @RequestParam(required = false) List<TaskStatus> status,
                                          @RequestParam(required = false) List<TaskPriority> priority,
                                          @RequestParam(required = false) String tag,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
                                          @RequestParam(required = false) Boolean overdue){
        TaskFilterRequest filter = new TaskFilterRequest(
                status, priority, tag, search, dueFrom, dueTo, overdue);
        TaskPageResponse response = service.list(filter, page, size, sortBy, sortDir);
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = "application/json",
            produces = "application/json")
    ResponseEntity<TaskResponse> create(@Validated(Create.class) @RequestBody TaskRequest request){
        TaskResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/tasks/" + response.id()))
                .body(response);
    }

    @PatchMapping(value = "/{id}",
            consumes = "application/json",
            produces = "application/json")
    ResponseEntity<TaskResponse> update(@PathVariable Long id, @Validated(Update.class) @RequestBody TaskRequest request){
        TaskResponse response = service.update(id, request);
        return ResponseEntity
                .created(URI.create("/api/tasks/" + response.id()))
                .body(response);
    }

    @PatchMapping(value = "/{id}/status",
            consumes = "application/json",
            produces = "application/json")
    ResponseEntity<TaskResponse> changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request){
        TaskResponse response = service.changeStatus(id, request.status());
        return ResponseEntity
                .created(URI.create("/api/tasks/" + response.id()))
                .body(response);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
