package com.s5teixeira.sales_dashboard.dto;

import java.util.List;

import com.s5teixeira.sales_dashboard.model.SalesFilter;

/** Everything the dashboard needs for one filter, returned in a single API call. */
public record DashboardData(
        SalesFilter filter,
        boolean hasData,
        Kpis kpis,
        List<MonthlyRevenue> monthly,
        List<CategoryRevenue> categories,
        List<ProductRevenue> topProducts,
        List<RegionPerformance> regions,
        List<StatusBreakdown> statuses) {
}
