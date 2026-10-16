package com.s5teixeira.sales_dashboard.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.s5teixeira.sales_dashboard.TestData;
import com.s5teixeira.sales_dashboard.service.DataImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PageControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DataImportService importService;

    @Test
    void emptyDatabaseShowsOnlyUploadCsvAndLoadSampleData() throws Exception {
        importService.deleteDataset();

        for (String page : new String[]{"/", "/transactions"}) {
            mvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"upload-csv\">Upload CSV</button>")))
                    .andExpect(content().string(containsString("id=\"load-sample\">Load Sample Data</button>")))
                    .andExpect(content().string(not(containsString("Delete Dataset"))))
                    .andExpect(content().string(not(containsString("class=\"kpi\""))))
                    .andExpect(content().string(not(containsString("<canvas"))))
                    .andExpect(content().string(not(containsString("<table"))))
                    .andExpect(content().string(not(containsString("No sales data"))));
        }
    }

    @Test
    void loadedDatasetShowsTheDashboardAndDeleteButton() throws Exception {
        TestData.loadSmallFixture(importService);

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sales Performance")))
                .andExpect(content().string(containsString("Delete Dataset")))
                .andExpect(content().string(containsString("id=\"chart-monthly\"")))
                .andExpect(content().string(containsString("<option value=\"Electronics\">Electronics</option>")))
                .andExpect(content().string(not(containsString("id=\"upload-csv\""))))
                .andExpect(content().string(not(containsString("id=\"load-sample\""))));
        mvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Transaction Explorer")))
                .andExpect(content().string(containsString("Delete Dataset")));
    }

    @Test
    void dataAndMethodologyPageIsRemoved() throws Exception {
        TestData.loadSmallFixture(importService);
        mvc.perform(get("/data")).andExpect(status().isNotFound());
        mvc.perform(get("/"))
                .andExpect(content().string(not(containsString("Methodology"))))
                .andExpect(content().string(not(containsString("href=\"/data\""))));
    }

    @Test
    void logoAndMetricsExplanationAreRemoved() throws Exception {
        TestData.loadSmallFixture(importService);
        mvc.perform(get("/"))
                .andExpect(content().string(not(containsString("class=\"brand"))))
                .andExpect(content().string(not(containsString("<svg"))))
                .andExpect(content().string(not(containsString("How these metrics are calculated"))));
    }

    @Test
    void navigationShowsOnlyDashboardAndTransactionsAndThereIsNoFooter() throws Exception {
        TestData.loadSmallFixture(importService);
        for (String page : new String[]{"/", "/transactions"}) {
            mvc.perform(get(page))
                    .andExpect(content().string(containsString(">Dashboard</a>")))
                    .andExpect(content().string(containsString(">Transactions</a>")))
                    .andExpect(content().string(not(containsString(">Sales Dashboard<"))))
                    .andExpect(content().string(not(containsString("<footer"))));
        }
    }

    @Test
    void dashboardSubtitleIsRemovedAndTransactionsShowOrderAmount() throws Exception {
        TestData.loadSmallFixture(importService);
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("<h1>Sales Performance</h1>")))
                .andExpect(content().string(not(containsString("Recognized revenue from completed orders"))));
        mvc.perform(get("/transactions"))
                .andExpect(content().string(containsString("Order amount")))
                .andExpect(content().string(not(containsString("Net amount"))));
    }

    @Test
    void pagesAreNeverCachedByTheBrowser() throws Exception {
        mvc.perform(get("/")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/dashboard")).andExpect(header().string("Cache-Control", "no-store"));
    }
}
