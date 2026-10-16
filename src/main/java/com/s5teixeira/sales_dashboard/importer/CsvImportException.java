package com.s5teixeira.sales_dashboard.importer;

import java.util.List;

/** Thrown when a CSV file fails validation. Nothing is written to the database in that case. */
public class CsvImportException extends RuntimeException {

    private final List<String> errors;

    public CsvImportException(List<String> errors) {
        super("CSV validation failed with " + errors.size() + " error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
