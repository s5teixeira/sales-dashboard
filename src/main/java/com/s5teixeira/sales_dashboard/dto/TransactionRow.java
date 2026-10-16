package com.s5teixeira.sales_dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One order in the transaction explorer, with totals across all of its line items. */
public record TransactionRow(
        String orderId,
        LocalDate orderDate,
        String customerId,
        String region,
        String status,
        int itemCount,
        long units,
        String categories,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal netAmount) {
}
