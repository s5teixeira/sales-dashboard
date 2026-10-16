package com.s5teixeira.sales_dashboard.dto;

import java.time.LocalDate;
import java.util.List;

/** Values for the filter dropdowns, read from the database. */
public record FilterOptions(
        List<String> categories,
        List<String> regions,
        LocalDate minDate,
        LocalDate maxDate) {
}
