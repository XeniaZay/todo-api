package com.example.todo.task.exception;

import com.example.todo.task.TaskStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    private final TaskStatus from;
    private final TaskStatus to;

    public InvalidStatusTransitionException(TaskStatus from, TaskStatus to) {
        super("Cannot transition task status from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public TaskStatus getFrom() { return from; }
    public TaskStatus getTo() { return to; }
}