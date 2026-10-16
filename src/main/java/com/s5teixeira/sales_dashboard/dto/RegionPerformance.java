package com.s5teixeira.sales_dashboard.dto;

import java.math.BigDecimal;

public record RegionPerformance(
        String region,
        BigDecimal revenue,
        long completedOrders,
        BigDecimal averageOrderValue,
        BigDecimal returnRatePct,
        BigDecimal sharePct) {
}
