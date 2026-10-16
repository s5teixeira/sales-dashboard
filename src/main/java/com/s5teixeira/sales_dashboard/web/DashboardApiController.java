package com.s5teixeira.sales_dashboard.web;

import com.s5teixeira.sales_dashboard.dto.DashboardData;
import com.s5teixeira.sales_dashboard.dto.FilterOptions;
import com.s5teixeira.sales_dashboard.model.SalesFilter;
import com.s5teixeira.sales_dashboard.service.DashboardService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardApiController {

    private final DashboardService dashboardService;

    public DashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** KPIs and chart data. Query params: startDate, endDate (YYYY-MM-DD), category, region. */
    @GetMapping("/dashboard")
    public DashboardData dashboard(@Valid SalesFilter filter) {
        return dashboardService.dashboard(filter);
    }

    @GetMapping("/filters")
    public FilterOptions filters() {
        return dashboardService.filterOptions();
    }
}
