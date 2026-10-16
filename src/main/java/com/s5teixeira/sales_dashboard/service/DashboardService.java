package com.s5teixeira.sales_dashboard.service;

import java.time.LocalDate;
import java.util.List;

import com.s5teixeira.sales_dashboard.dto.DashboardData;
import com.s5teixeira.sales_dashboard.dto.FilterOptions;
import com.s5teixeira.sales_dashboard.dto.Kpis;
import com.s5teixeira.sales_dashboard.dto.MonthlyGrowth;
import com.s5teixeira.sales_dashboard.dto.MonthlyRevenue;
import com.s5teixeira.sales_dashboard.model.SalesFilter;
import com.s5teixeira.sales_dashboard.repository.AnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    static final int TOP_PRODUCT_LIMIT = 10;

    private final AnalyticsRepository analytics;

    public DashboardService(AnalyticsRepository analytics) {
        this.analytics = analytics;
    }

    /** Runs every dashboard query for one filter inside a read-only transaction (a consistent snapshot). */
    @Transactional(readOnly = true)
    public DashboardData dashboard(SalesFilter filter) {
        FilterOptions options = analytics.filterOptions();
        boolean hasData = options.minDate() != null;

        List<MonthlyRevenue> monthly = monthlySeries(filter, options);
        Kpis kpis = analytics.kpis(filter).withMonthlyGrowth(latestGrowth(filter, monthly));

        return new DashboardData(filter, hasData, kpis, monthly,
                analytics.revenueByCategory(filter),
                analytics.topProducts(filter, TOP_PRODUCT_LIMIT),
                analytics.performanceByRegion(filter),
                analytics.statusBreakdown(filter));
    }

    public FilterOptions filterOptions() {
        return analytics.filterOptions();
    }

    /**
     * The month axis covers the overlap of the selected date range and the stored data, so a wide
     * date filter does not produce a long run of empty months.
     */
    private List<MonthlyRevenue> monthlySeries(SalesFilter filter, FilterOptions options) {
        if (options.minDate() == null) {
            return List.of();
        }
        LocalDate from = later(filter.startDate(), options.minDate());
        LocalDate to = earlier(filter.endDate(), options.maxDate());
        if (from.isAfter(to)) {
            return List.of();
        }
        return analytics.monthlyRevenue(filter, from.withDayOfMonth(1), to.withDayOfMonth(1));
    }

    /**
     * Month-over-month growth of the latest month in range. If the date filter ends before the last
     * day of that month, it is flagged as partial so the UI can warn that the comparison is incomplete.
     */
    static MonthlyGrowth latestGrowth(SalesFilter filter, List<MonthlyRevenue> monthly) {
        if (monthly.isEmpty()) {
            return null;
        }
        MonthlyRevenue latest = monthly.getLast();
        LocalDate monthEnd = latest.month().plusMonths(1).minusDays(1);
        boolean partial = filter.endDate() != null && filter.endDate().isBefore(monthEnd);
        LocalDate previousMonth = monthly.size() > 1 ? monthly.get(monthly.size() - 2).month() : null;
        return new MonthlyGrowth(latest.month(), previousMonth, latest.revenue(), latest.previousRevenue(),
                latest.growthPct(), partial);
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a == null || a.isBefore(b) ? b : a;
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a == null || a.isAfter(b) ? b : a;
    }
}
