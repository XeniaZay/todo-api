package com.example.todo.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>,
        JpaSpecificationExecutor<Task> {
    // Пока стандартные методы: save, findById, findAll, deleteById
    // Свои методы добавим на этапе 4 (фильтры + пагинация)
}