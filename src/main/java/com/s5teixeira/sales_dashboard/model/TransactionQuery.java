package com.s5teixeira.sales_dashboard.model;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/** Search, filter, sort and paging options for the transaction explorer. */
public record TransactionQuery(
        @Size(max = 40) String search,
        OrderStatus status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @Size(max = 60) String category,
        @Size(max = 40) String region,
        TransactionSort sort,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size) {

    public static final int DEFAULT_SIZE = 20;

    public TransactionQuery {
        search = SalesFilter.blankToNull(search);
        category = SalesFilter.blankToNull(category);
        region = SalesFilter.blankToNull(region);
        sort = sort == null ? TransactionSort.DATE_DESC : sort;
        page = page == null ? 0 : page;
        size = size == null ? DEFAULT_SIZE : size;
    }

    @JsonIgnore
    @AssertTrue(message = "startDate must be on or before endDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }
}
