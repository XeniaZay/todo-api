package com.example.todo.common;

import com.example.todo.task.exception.InvalidStatusTransitionException;
import com.example.todo.task.exception.TaskNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ============================================================
    // 1. Валидация DTO (@Valid @RequestBody) → 422
    // ============================================================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex,
                                   HttpServletRequest request) {

        List<Map<String, String>> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "code", fe.getCode() != null ? fe.getCode() : "INVALID",
                        "message", fe.getDefaultMessage() != null
                                ? fe.getDefaultMessage() : "invalid value"
                ))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Validation failed for " + violations.size() + " field(s)"
        );
        problem.setTitle("Validation failed");
        problem.setType(URI.create("https://api.example.com/problems/validation-error"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("violations", violations);
        addRequestId(problem);

        log.warn("Validation failed: path={} violations={}",
                request.getRequestURI(), violations);

        return problem;
    }

    // ============================================================
    // 2. Кривой JSON → 400
    // ============================================================
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException ex,
                                   HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON or unknown field in request body"
        );
        problem.setTitle("Invalid JSON");
        problem.setType(URI.create("https://api.example.com/problems/invalid-json"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "JSON_INVALID");
        problem.setProperty("timestamp", Instant.now().toString());
        addRequestId(problem);

        Throwable cause = ex.getMostSpecificCause();
        if (cause.getMessage() != null) {
            problem.setProperty("details", cause.getMessage());
        }

        log.warn("Invalid JSON: path={} reason={}",
                request.getRequestURI(), cause.getMessage());

        return problem;
    }

    // ============================================================
    // 3. Неверный тип query-параметра → 400
    // ============================================================
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                     HttpServletRequest request) {
        String value = ex.getValue() != null ? ex.getValue().toString() : "null";
        String requiredType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName() : "unknown";

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid value '" + value + "' for parameter '" + ex.getName()
                        + "'. Expected type: " + requiredType
        );
        problem.setTitle("Invalid parameter type");
        problem.setType(URI.create("https://api.example.com/problems/invalid-parameter-type"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_PARAMETER_TYPE");
        problem.setProperty("parameter", ex.getName());
        problem.setProperty("invalidValue", value);
        problem.setProperty("expectedType", requiredType);
        problem.setProperty("timestamp", Instant.now().toString());
        addRequestId(problem);

        log.warn("Parameter type mismatch: parameter={} value={} expected={}",
                ex.getName(), value, requiredType);

        return problem;
    }

    // ============================================================
    // 4. TaskNotFoundException → 404
    // ============================================================
    @ExceptionHandler(TaskNotFoundException.class)
    ProblemDetail handleTaskNotFound(TaskNotFoundException ex,
                                     HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setTitle("Task not found");
        problem.setType(URI.create("https://api.example.com/problems/task-not-found"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "TASK_NOT_FOUND");
        problem.setProperty("taskId", ex.getTaskId());
        problem.setProperty("timestamp", Instant.now().toString());
        addRequestId(problem);

        log.warn("Task not found: id={} path={}",
                ex.getTaskId(), request.getRequestURI());

        return problem;
    }

    // ============================================================
    // 5. InvalidStatusTransitionException → 409 Conflict
    // ============================================================
    @ExceptionHandler(InvalidStatusTransitionException.class)
    ProblemDetail handleInvalidStatusTransition(InvalidStatusTransitionException ex,
                                                HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Invalid status transition");
        problem.setType(URI.create("https://api.example.com/problems/invalid-status-transition"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_STATUS_TRANSITION");
        problem.setProperty("from", ex.getFrom().name());
        problem.setProperty("to", ex.getTo().name());
        problem.setProperty("timestamp", Instant.now().toString());
        addRequestId(problem);

        log.warn("Invalid status transition: from={} to={}",
                ex.getFrom(), ex.getTo());

        return problem;
    }

    // ============================================================
    // 6. Fallback → 500 (без stacktrace!)
    // ============================================================
    @ExceptionHandler(Exception.class)
    ProblemDetail handleAll(Exception ex, HttpServletRequest request) {
        // Пропускаем Spring MVC-исключения — у них свой статус
        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            try {
                throw (Throwable) errorResponse;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error occurred"
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://api.example.com/problems/internal-error"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INTERNAL_ERROR");
        problem.setProperty("timestamp", Instant.now().toString());
        addRequestId(problem);

        log.error("Unhandled error: path={}", request.getRequestURI(), ex);

        return problem;
    }

    // ============================================================
    // Вспомогательные
    // ============================================================
    private void addRequestId(ProblemDetail problem) {
        String requestId = MDC.get("requestId");
        if (requestId == null) requestId = MDC.get("traceId");
        if (requestId == null) requestId = MDC.get("correlationId");
        if (requestId != null && !requestId.isBlank()) {
            problem.setProperty("requestId", requestId);
        }
    }
}