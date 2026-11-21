package com.geofiber.geofiber_backend.controllers.errors;

import com.geofiber.geofiber_backend.services.imports.ImportValidationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Todas as respostas de erro saem como ProblemDetail (RFC 9457). Erros de validação trazem a
 * propriedade {@code errors} com {@code field} e {@code message} de cada campo inválido.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    static final String GENERIC_CONFLICT = "The requested change conflicts with existing infrastructure data.";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request content.");
        problem.setTitle("Validation failed");
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
            .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ImportValidationException.class)
    public ProblemDetail handleImportValidation(ImportValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid import");
        problem.setProperty("errors", exception.getErrors());
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataConflict(DataIntegrityViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            ConstraintMessages.describe(constraintName(exception)).orElse(GENERIC_CONFLICT));
        problem.setTitle("Infrastructure data conflict");
        return problem;
    }

    /** Último recurso: qualquer erro não tratado vira ProblemDetail 500, sem expor detalhes internos. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        logger.error("Unexpected error", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Unexpected error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static String constraintName(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
