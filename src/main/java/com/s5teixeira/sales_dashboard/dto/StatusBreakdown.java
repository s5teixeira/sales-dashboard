package com.s5teixeira.sales_dashboard.dto;

import java.math.BigDecimal;

/** Order count and merchandise value (after discounts) per order status. */
public record StatusBreakdown(
        String status,
        long orders,
        BigDecimal orderValue,
        BigDecimal sharePct) {
}
