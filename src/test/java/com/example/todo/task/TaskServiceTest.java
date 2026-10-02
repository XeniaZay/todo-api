package com.example.todo.task;

import com.example.todo.task.dto.TaskFilterRequest;
import com.example.todo.task.dto.TaskPageResponse;
import com.example.todo.task.dto.TaskRequest;
import com.example.todo.task.dto.TaskResponse;
import com.example.todo.task.exception.InvalidStatusTransitionException;
import com.example.todo.task.exception.TaskNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    private TaskMetrics metrics;
    private TaskService service;

    @BeforeEach
    void setUp() {
        // Реальный TaskMetrics с SimpleMeterRegistry — не мокаем, хотим проверить поведение
        metrics = new TaskMetrics(new SimpleMeterRegistry());
        service = new TaskService(repository, metrics);
    }

    // ============================================================
    // CREATE
    // ============================================================

    @Test
    void create_validRequest_returnsTaskResponse() {
        TaskRequest request = new TaskRequest(
                "Buy milk", "2% fat", TaskPriority.MEDIUM,
                LocalDate.now().plusDays(1), Set.of("shopping")
        );

        Task saved = newTask(1L, "Buy milk", TaskStatus.TODO, TaskPriority.MEDIUM);
        when(repository.save(any(Task.class))).thenReturn(saved);
        when(repository.count(any(Specification.class))).thenReturn(0L);

        TaskResponse response = service.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Buy milk");
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);

        verify(repository).save(any(Task.class));
    }

    // ============================================================
    // READ
    // ============================================================

    @Test
    void getById_existingTask_returnsResponse() {
        Task task = newTask(42L, "Read book", TaskStatus.TODO, TaskPriority.LOW);
        when(repository.findById(42L)).thenReturn(Optional.of(task));

        TaskResponse response = service.getById(42L);

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.title()).isEqualTo("Read book");
    }

    @Test
    void getById_missingTask_throwsTaskNotFoundException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ============================================================
    // LIST
    // ============================================================

    @Test
    void list_returnsPagedResponse() {
        Task t1 = newTask(1L, "A", TaskStatus.TODO, TaskPriority.LOW);
        Task t2 = newTask(2L, "B", TaskStatus.TODO, TaskPriority.HIGH);
        Page<Task> page = new PageImpl<>(List.of(t1, t2), PageRequest.of(0, 10), 2);

        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        TaskFilterRequest filter = new TaskFilterRequest(
                null, null, null, null, null, null, null);
        TaskPageResponse response = service.list(filter, 0, 10, "id", "asc");

        assertThat(response.content()).hasSize(2);
        assertThat(response.total()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    // ============================================================
    // UPDATE
    // ============================================================

    @Test
    void update_partialFields_onlyChangesProvided() {
        Task existing = newTask(1L, "Old title", TaskStatus.TODO, TaskPriority.LOW);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskRequest patch = new TaskRequest(
                "New title", null, null, null, null);

        TaskResponse response = service.update(1L, patch);

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.priority()).isEqualTo(TaskPriority.LOW);   // не менялся
        assertThat(response.status()).isEqualTo(TaskStatus.TODO);       // не менялся
    }

    @Test
    void update_missingTask_throwsTaskNotFoundException() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        TaskRequest patch = new TaskRequest("New", null, null, null, null);

        assertThatThrownBy(() -> service.update(999L, patch))
                .isInstanceOf(TaskNotFoundException.class);
    }

    // ============================================================
    // CHANGE STATUS
    // ============================================================

    @Test
    void changeStatus_validTransition_updatesStatus() {
        Task task = newTask(1L, "Task", TaskStatus.TODO, TaskPriority.LOW);
        when(repository.findById(1L)).thenReturn(Optional.of(task));
        when(repository.count(any(Specification.class))).thenReturn(0L);

        TaskResponse response = service.changeStatus(1L, TaskStatus.IN_PROGRESS);

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void changeStatus_invalidTransition_throwsException() {
        Task task = newTask(1L, "Task", TaskStatus.DONE, TaskPriority.LOW);
        when(repository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.changeStatus(1L, TaskStatus.TODO))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("DONE")
                .hasMessageContaining("TODO");
    }

    // ============================================================
    // DELETE
    // ============================================================

    @Test
    void delete_existingTask_deletesAndReturnsVoid() {
        when(repository.existsById(1L)).thenReturn(true);
        when(repository.count(any(Specification.class))).thenReturn(0L);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_missingTask_throwsTaskNotFoundException() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Task newTask(Long id, String title, TaskStatus status, TaskPriority priority) {
        Task task = new Task(
                title, "description", priority,
                LocalDate.now().plusDays(1), Set.of("test")
        );
        // Устанавливаем id через рефлексию (поле id без сеттера)
        try {
            var field = Task.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(task, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        try {
            var statusField = Task.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(task, status);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return task;
    }
}