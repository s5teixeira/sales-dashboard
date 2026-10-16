package com.s5teixeira.sales_dashboard.dto;

import java.math.BigDecimal;

public record CategoryRevenue(
        String category,
        BigDecimal revenue,
        long unitsSold,
        long orders,
        BigDecimal sharePct) {
}
