package com.s5teixeira.sales_dashboard.dto;

import java.math.BigDecimal;

public record ProductRevenue(
        String productName,
        String category,
        BigDecimal revenue,
        long unitsSold,
        long orders) {
}
