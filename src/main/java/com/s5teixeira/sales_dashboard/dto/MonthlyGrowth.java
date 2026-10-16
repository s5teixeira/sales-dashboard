package com.s5teixeira.sales_dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Month-over-month revenue growth for the latest month in the selected range.
 * growthPct is null when there is no prior month in range or the prior month had zero revenue.
 * partialMonth is true when the date filter cuts the latest month short.
 */
public record MonthlyGrowth(
        LocalDate month,
        LocalDate previousMonth,
        BigDecimal revenue,
        BigDecimal previousRevenue,
        BigDecimal growthPct,
        boolean partialMonth) {
}
