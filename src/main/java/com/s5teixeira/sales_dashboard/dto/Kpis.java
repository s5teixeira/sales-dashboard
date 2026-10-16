package com.s5teixeira.sales_dashboard.dto;

import java.math.BigDecimal;

/**
 * Headline KPIs for the current filter. Nullable ratios are null when the denominator is zero,
 * so the UI shows "N/A" instead of a misleading 0%.
 *
 * @param totalRevenue       net revenue of COMPLETED orders
 * @param completedOrders    distinct COMPLETED orders
 * @param averageOrderValue  totalRevenue / completedOrders (null if no completed orders)
 * @param unitsSold          units on COMPLETED orders
 * @param returnRatePct      RETURNED orders / non-cancelled orders x 100 (null if none)
 * @param uniqueCustomers    distinct customers with at least one COMPLETED order
 * @param monthlyGrowth      latest month in range vs the month before it
 */
public record Kpis(
        BigDecimal totalRevenue,
        long completedOrders,
        BigDecimal averageOrderValue,
        long unitsSold,
        long returnedOrders,
        long cancelledOrders,
        long nonCancelledOrders,
        long totalOrders,
        BigDecimal returnRatePct,
        long uniqueCustomers,
        MonthlyGrowth monthlyGrowth) {

    public Kpis withMonthlyGrowth(MonthlyGrowth growth) {
        return new Kpis(totalRevenue, completedOrders, averageOrderValue, unitsSold, returnedOrders,
                cancelledOrders, nonCancelledOrders, totalOrders, returnRatePct, uniqueCustomers, growth);
    }
}
