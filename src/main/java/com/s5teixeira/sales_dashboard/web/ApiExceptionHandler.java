package com.s5teixeira.sales_dashboard.web;

import java.util.List;

import com.s5teixeira.sales_dashboard.importer.CsvImportException;
import com.s5teixeira.sales_dashboard.importer.DatasetAlreadyLoadedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Turns validation and import failures into RFC 9457 problem responses with readable messages. */
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class ApiExceptionHandler {

    // DuplicateKeyException covers the rare case of two imports starting at the same moment:
    // the primary keys reject the second one and its transaction is rolled back.
    @ExceptionHandler({DatasetAlreadyLoadedException.class, DuplicateKeyException.class})
    public ProblemDetail datasetAlreadyLoaded() {
        return problem(HttpStatus.CONFLICT, "A dataset is already loaded",
                List.of(new DatasetAlreadyLoadedException().getMessage()));
    }

    @ExceptionHandler(CsvImportException.class)
    public ProblemDetail csvImport(CsvImportException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "The CSV file was not imported", e.getErrors());
    }

    /** Invalid filter or query parameters (bad date format, unknown status, startDate after endDate...). */
    @ExceptionHandler(BindException.class)
    public ProblemDetail invalidParameters(BindException e) {
        List<String> errors = e.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    String field = error instanceof org.springframework.validation.FieldError fe ? fe.getField() : "request";
                    if (error.contains(org.springframework.beans.TypeMismatchException.class)) {
                        return field + ": invalid value";
                    }
                    // Cross-field checks (@AssertTrue methods) already carry a complete message.
                    return field.endsWith("Valid") ? error.getDefaultMessage() : field + ": " + error.getDefaultMessage();
                })
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameters", errors);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", List.of(e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "File too large", List.of("The maximum upload size is 5 MB"));
    }

    private static ProblemDetail problem(HttpStatus status, String title, List<String> errors) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setTitle(title);
        detail.setDetail(errors.isEmpty() ? title : errors.getFirst());
        detail.setProperty("errors", errors);
        return detail;
    }
}
