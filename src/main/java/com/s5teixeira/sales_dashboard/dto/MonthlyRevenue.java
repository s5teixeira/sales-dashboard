package com.s5teixeira.sales_dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One month of completed sales. growthPct is null when the previous month is missing or zero. */
public record MonthlyRevenue(
        LocalDate month,
        BigDecimal revenue,
        long completedOrders,
        long unitsSold,
        BigDecimal previousRevenue,
        BigDecimal growthPct) {
}
