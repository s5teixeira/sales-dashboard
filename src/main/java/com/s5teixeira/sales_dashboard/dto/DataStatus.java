package com.s5teixeira.sales_dashboard.dto;

import java.time.LocalDate;

public record DataStatus(
        long orders,
        long lineItems,
        long customers,
        LocalDate minDate,
        LocalDate maxDate) {

    public boolean hasData() {
        return orders > 0;
    }
}
