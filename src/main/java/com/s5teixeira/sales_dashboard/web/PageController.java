package com.s5teixeira.sales_dashboard.web;

import com.s5teixeira.sales_dashboard.service.DashboardService;
import com.s5teixeira.sales_dashboard.service.DataImportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Server-rendered pages. Each request checks the database, so the page shows either the
 * Upload CSV / Load Sample Data actions (empty database) or the dashboard (dataset loaded).
 * Charts and tables are then filled in by JavaScript from the REST API.
 */
@Controller
public class PageController {

    private final DashboardService dashboardService;
    private final DataImportService importService;

    public PageController(DashboardService dashboardService, DataImportService importService) {
        this.dashboardService = dashboardService;
        this.importService = importService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        addCommonAttributes(model, "dashboard");
        return "dashboard";
    }

    @GetMapping("/transactions")
    public String transactions(Model model) {
        addCommonAttributes(model, "transactions");
        return "transactions";
    }

    private void addCommonAttributes(Model model, String page) {
        model.addAttribute("page", page);
        model.addAttribute("hasData", importService.status().hasData());
        model.addAttribute("options", dashboardService.filterOptions());
    }
}
