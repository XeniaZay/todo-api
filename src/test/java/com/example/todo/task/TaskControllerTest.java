package com.example.todo.task;

import com.example.todo.common.GlobalExceptionHandler;
import com.example.todo.task.dto.TaskResponse;
import com.example.todo.task.exception.InvalidStatusTransitionException;
import com.example.todo.task.exception.TaskNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService service;

    // ============================================================
    // POST /api/tasks — CREATE
    // ============================================================

    @Test
    void create_validRequest_returns201WithLocation() throws Exception {
        TaskResponse response = sampleResponse(1L, "Buy milk", TaskStatus.TODO);
        when(service.create(any())).thenReturn(response);

        String body = """
                {
                  "title": "Buy milk",
                  "description": "2% fat",
                  "priority": "MEDIUM",
                  "due_date": "2026-12-31",
                  "tags": ["shopping"]
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", endsWith("/api/tasks/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Buy milk"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void create_blankTitle_returns422WithViolation() throws Exception {
        String body = """
                {
                  "title": "",
                  "priority": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.violations[*].field", hasItem("title")))
                .andExpect(jsonPath("$.violations[*].code", hasItem("NotBlank")));
    }

    @Test
    void create_invalidPriority_returns400JsonInvalid() throws Exception {
        String body = """
                {
                  "title": "Valid",
                  "priority": "SUPER_URGENT"
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JSON_INVALID"))
                .andExpect(jsonPath("$.details", containsString("SUPER_URGENT")));
    }

    // ============================================================
    // GET /api/tasks/{id} — READ
    // ============================================================

    @Test
    void getById_existingTask_returns200() throws Exception {
        when(service.getById(1L)).thenReturn(sampleResponse(1L, "Read book", TaskStatus.DONE));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Read book"))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void getById_missingTask_returns404() throws Exception {
        when(service.getById(999L)).thenThrow(new TaskNotFoundException(999L));

        mockMvc.perform(get("/api/tasks/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.taskId").value(999));
    }

    // ============================================================
    // PATCH /api/tasks/{id}/status — CHANGE STATUS
    // ============================================================

    @Test
    void changeStatus_validTransition_returns200() throws Exception {
        when(service.changeStatus(eq(1L), eq(TaskStatus.IN_PROGRESS)))
                .thenReturn(sampleResponse(1L, "Task", TaskStatus.IN_PROGRESS));

        mockMvc.perform(patch("/api/tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_PROGRESS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void changeStatus_invalidTransition_returns409() throws Exception {
        when(service.changeStatus(eq(1L), eq(TaskStatus.TODO)))
                .thenThrow(new InvalidStatusTransitionException(TaskStatus.DONE, TaskStatus.TODO));

        mockMvc.perform(patch("/api/tasks/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "TODO"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.from").value("DONE"))
                .andExpect(jsonPath("$.to").value("TODO"));
    }

    // ============================================================
    // DELETE /api/tasks/{id}
    // ============================================================

    @Test
    void delete_existingTask_returns204() throws Exception {
        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_missingTask_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new TaskNotFoundException(999L))
                .when(service).delete(999L);

        mockMvc.perform(delete("/api/tasks/999"))
                .andExpect(status().isNotFound());
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private TaskResponse sampleResponse(Long id, String title, TaskStatus status) {
        return new TaskResponse(
                id, title, "description", TaskPriority.MEDIUM,
                status, LocalDate.of(2026, 12, 31),
                Set.of("test"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}