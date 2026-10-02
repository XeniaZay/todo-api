package com.example.todo.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TaskRepositoryTest {

    @Autowired
    private TaskRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new Task("Buy milk", "From shop", TaskPriority.MEDIUM,
                        LocalDate.of(2026, 12, 1), Set.of("shopping", "home")),
                new Task("Finish course", "Spring module", TaskPriority.HIGH,
                        LocalDate.of(2026, 11, 1), Set.of("study", "spring")),
                new Task("Pay bills", "Monthly", TaskPriority.URGENT,
                        LocalDate.of(2026, 10, 1), Set.of("finance", "home"))
        ));
    }

    @Test
    void findAll_returnsAllTasks() {
        assertThat(repository.findAll()).hasSize(3);
    }

    @Test
    void findByStatus_filtersCorrectly() {
        Specification<Task> spec = TaskSpecifications.hasStatusIn(List.of(TaskStatus.TODO));
        Page<Task> page = repository.findAll(spec, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(3);   // все TODO по умолчанию
    }

    @Test
    void findByPriority_filtersCorrectly() {
        Specification<Task> spec = TaskSpecifications.hasPriorityIn(List.of(TaskPriority.HIGH));
        List<Task> result = repository.findAll(spec);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Finish course");
    }

    @Test
    void findByTag_filtersCorrectly() {
        Specification<Task> spec = TaskSpecifications.hasTag("home");
        List<Task> result = repository.findAll(spec);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Buy milk", "Pay bills");
    }

    @Test
    void searchInTitleOrDescription_filtersCorrectly() {
        Specification<Task> spec = TaskSpecifications.containsSearch("spring");
        List<Task> result = repository.findAll(spec);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Finish course");
    }

    @Test
    void searchIsCaseInsensitive() {
        Specification<Task> spec = TaskSpecifications.containsSearch("SPRING");
        List<Task> result = repository.findAll(spec);

        assertThat(result).hasSize(1);
    }

    @Test
    void dueDateBetween_filtersCorrectly() {
        Specification<Task> spec = TaskSpecifications.dueDateBetween(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 12, 31)
        );
        List<Task> result = repository.findAll(spec);

        assertThat(result).hasSize(2);       // Buy milk и Finish course
    }

    @Test
    void pagination_worksCorrectly() {
        Page<Task> page = repository.findAll(
                Specification.allOf(),
                PageRequest.of(0, 2, Sort.by("id"))
        );

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void combinedFilters_workCorrectly() {
        Specification<Task> spec = Specification
                .where(TaskSpecifications.hasTag("home"))
                .and(TaskSpecifications.hasPriorityIn(List.of(TaskPriority.URGENT)));

        List<Task> result = repository.findAll(spec);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Pay bills");
    }
}