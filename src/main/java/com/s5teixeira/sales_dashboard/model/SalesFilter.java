package com.s5teixeira.sales_dashboard.model;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Dashboard filters. Every field is optional; blank strings are treated as "no filter".
 * Dates are inclusive and apply to the order date.
 */
public record SalesFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @Size(max = 60) String category,
        @Size(max = 40) String region) {

    public SalesFilter {
        category = blankToNull(category);
        region = blankToNull(region);
    }

    public static SalesFilter none() {
        return new SalesFilter(null, null, null, null);
    }

    @JsonIgnore
    @AssertTrue(message = "startDate must be on or before endDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
