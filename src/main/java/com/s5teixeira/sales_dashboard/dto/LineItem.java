package com.s5teixeira.sales_dashboard.dto;

import java.math.BigDecimal;

public record LineItem(
        String lineItemId,
        String productName,
        String category,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal lineNet) {
}
