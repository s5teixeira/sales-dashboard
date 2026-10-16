package com.s5teixeira.sales_dashboard.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.s5teixeira.sales_dashboard.TestData;
import com.s5teixeira.sales_dashboard.service.DataImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DataImportService importService;

    @BeforeEach
    void loadFixture() {
        TestData.loadSmallFixture(importService);
    }

    // ---------- Dashboard ----------

    @Test
    void dashboardReturnsKpisAndChartSeries() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasData").value(true))
                .andExpect(jsonPath("$.kpis.totalRevenue").value(343.33))
                .andExpect(jsonPath("$.kpis.completedOrders").value(4))
                .andExpect(jsonPath("$.kpis.returnRatePct").value(20.00))
                .andExpect(jsonPath("$.kpis.monthlyGrowth.month").value("2026-04-01"))
                .andExpect(jsonPath("$.monthly", hasSize(4)))
                .andExpect(jsonPath("$.monthly[2].growthPct").value(nullValue()))
                .andExpect(jsonPath("$.topProducts[0].productName").value("Widget"))
                .andExpect(jsonPath("$.statuses", hasSize(3)));
    }

    @Test
    void dashboardAppliesFiltersFromQueryParameters() throws Exception {
        mvc.perform(get("/api/dashboard").param("category", "Electronics").param("region", "East"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalRevenue").value(180.00))
                .andExpect(jsonPath("$.kpis.completedOrders").value(1))
                .andExpect(jsonPath("$.categories", hasSize(1)));
    }

    @Test
    void dashboardRejectsInvalidDateRange() throws Exception {
        mvc.perform(get("/api/dashboard").param("startDate", "2026-05-01").param("endDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("startDate must be on or before endDate")));
    }

    @Test
    void dashboardRejectsMalformedDate() throws Exception {
        mvc.perform(get("/api/dashboard").param("startDate", "01/02/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("startDate: invalid value"));
    }

    @Test
    void filterOptionsComeFromTheDatabase() throws Exception {
        mvc.perform(get("/api/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories", hasSize(3)))
                .andExpect(jsonPath("$.regions", hasItem("West")))
                .andExpect(jsonPath("$.minDate").value("2026-01-10"));
    }

    // ---------- Transactions ----------

    @Test
    void transactionsArePagedAndSortedByDateDescendingByDefault() throws Exception {
        mvc.perform(get("/api/transactions").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(6))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].orderId").value("ORD-6"))
                .andExpect(jsonPath("$.rows[1].orderId").value("ORD-5"));
    }

    @Test
    void transactionsCanBeSortedAscendingAndByAmount() throws Exception {
        mvc.perform(get("/api/transactions").param("sort", "DATE_ASC"))
                .andExpect(jsonPath("$.rows[0].orderId").value("ORD-1"));
        mvc.perform(get("/api/transactions").param("sort", "AMOUNT_DESC"))
                .andExpect(jsonPath("$.rows[0].orderId").value("ORD-1"))
                .andExpect(jsonPath("$.rows[0].netAmount").value(200.00))
                .andExpect(jsonPath("$.rows[0].discountAmount").value(20.00))
                .andExpect(jsonPath("$.rows[0].itemCount").value(2))
                .andExpect(jsonPath("$.rows[0].categories").value("Electronics, Home"));
    }

    @Test
    void transactionsSearchMatchesOrderOrCustomerIdCaseInsensitively() throws Exception {
        mvc.perform(get("/api/transactions").param("search", "c1"))
                .andExpect(jsonPath("$.totalRows").value(2)); // ORD-1 and ORD-3 belong to C1
        mvc.perform(get("/api/transactions").param("search", "ord-4"))
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("CANCELLED"));
    }

    @Test
    void transactionsFilterByStatusAndCategory() throws Exception {
        mvc.perform(get("/api/transactions").param("status", "COMPLETED").param("category", "Clothing"))
                .andExpect(jsonPath("$.totalRows").value(2)); // ORD-2 and ORD-5
    }

    @Test
    void searchInputIsTreatedAsDataNotSql() throws Exception {
        mvc.perform(get("/api/transactions").param("search", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(0));
        mvc.perform(get("/api/transactions").param("search", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(0)); // LIKE wildcard is escaped
    }

    @Test
    void transactionsRejectUnknownSortOrStatus() throws Exception {
        mvc.perform(get("/api/transactions").param("sort", "order_date; DROP TABLE orders"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/transactions").param("status", "SHIPPED"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/transactions").param("size", "1000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderDetailIncludesLineItems() throws Exception {
        mvc.perform(get("/api/transactions/ord-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.netAmount").value(200.00))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].lineNet").value(180.00));
        mvc.perform(get("/api/transactions/NOPE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportReturnsCsvForTheFilteredOrders() throws Exception {
        mvc.perform(get("/api/transactions/export").param("status", "RETURNED"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("transactions.csv")))
                .andExpect(content().string(startsWith("order_id,order_date,customer_id")))
                .andExpect(content().string(containsString("ORD-3,2026-02-05,C1,East,RETURNED")));
    }

    // ---------- Dataset: upload, load sample, delete ----------

    private static final String CSV_HEADER =
            "order_id,order_date,customer_id,region,status,line_item_id,product_name,category,quantity,unit_price,discount_percent\n";

    @Test
    void uploadIntoAnEmptyDatabaseLoadsTheFile() throws Exception {
        importService.deleteDataset();
        MockMultipartFile file = new MockMultipartFile("file", "new.csv", "text/csv",
                (CSV_HEADER + "ORD-7,2026-04-20,C4,North,COMPLETED,L9,Lamp,Home,1,50.00,0\n").getBytes());

        mvc.perform(multipart("/api/data/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("new.csv"))
                .andExpect(jsonPath("$.orders").value(1))
                .andExpect(jsonPath("$.lineItems").value(1));
        mvc.perform(get("/api/dashboard")).andExpect(jsonPath("$.kpis.totalRevenue").value(50.00));
    }

    @Test
    void importsAreRejectedWhileADatasetIsLoaded() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "new.csv", "text/csv",
                (CSV_HEADER + "ORD-7,2026-04-20,C4,North,COMPLETED,L9,Lamp,Home,1,50.00,0\n").getBytes());

        mvc.perform(multipart("/api/data/upload").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]", containsString("Delete the current dataset")));
        mvc.perform(post("/api/data/sample")).andExpect(status().isConflict());
        mvc.perform(get("/api/data/status")).andExpect(jsonPath("$.orders").value(6));
    }

    @Test
    void invalidUploadReturns422AndLeavesTheDatabaseEmpty() throws Exception {
        importService.deleteDataset();
        MockMultipartFile file = new MockMultipartFile("file", "bad.csv", "text/csv",
                (CSV_HEADER + "ORD-7,2026-04-20,C4,North,SHIPPED,L9,Lamp,Home,1,50.00,0\n").getBytes());

        mvc.perform(multipart("/api/data/upload").file(file))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0]", containsString("status must be COMPLETED, CANCELLED or RETURNED")));
        mvc.perform(get("/api/data/status")).andExpect(jsonPath("$.orders").value(0));
    }

    @Test
    void uploadRejectsNonCsvFiles() throws Exception {
        importService.deleteDataset();
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx", "application/octet-stream", new byte[]{1, 2});
        mvc.perform(multipart("/api/data/upload").file(file))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void afterDeletingNoPreviousDataAppearsInAnyResponse() throws Exception {
        mvc.perform(delete("/api/data")).andExpect(status().isNoContent());

        mvc.perform(get("/api/data/status"))
                .andExpect(jsonPath("$.orders").value(0))
                .andExpect(jsonPath("$.lineItems").value(0));
        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.hasData").value(false))
                .andExpect(jsonPath("$.kpis.totalRevenue").value(0))
                .andExpect(jsonPath("$.kpis.totalOrders").value(0))
                .andExpect(jsonPath("$.kpis.averageOrderValue").value(nullValue()))
                .andExpect(jsonPath("$.monthly", hasSize(0)))
                .andExpect(jsonPath("$.categories", hasSize(0)))
                .andExpect(jsonPath("$.topProducts", hasSize(0)))
                .andExpect(jsonPath("$.regions", hasSize(0)))
                .andExpect(jsonPath("$.statuses", hasSize(0)));
        mvc.perform(get("/api/dashboard").param("category", "Electronics"))
                .andExpect(jsonPath("$.kpis.totalRevenue").value(0));
        mvc.perform(get("/api/filters"))
                .andExpect(jsonPath("$.categories", hasSize(0)))
                .andExpect(jsonPath("$.regions", hasSize(0)));
        mvc.perform(get("/api/transactions"))
                .andExpect(jsonPath("$.totalRows").value(0))
                .andExpect(jsonPath("$.rows", hasSize(0)));
        mvc.perform(get("/api/transactions/ORD-1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions/export"))
                .andExpect(content().string(
                        "order_id,order_date,customer_id,region,status,line_items,units,categories,gross_amount,discount_amount,order_amount\r\n"));
    }

    @Test
    void aNewDatasetLoadedAfterDeletingReplacesTheOldOneEverywhere() throws Exception {
        mvc.perform(delete("/api/data")).andExpect(status().isNoContent());
        mvc.perform(post("/api/data/sample"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").value(200));

        mvc.perform(get("/api/dashboard")).andExpect(jsonPath("$.kpis.totalOrders").value(200));
        mvc.perform(get("/api/filters"))
                .andExpect(jsonPath("$.categories", hasSize(5)))
                .andExpect(jsonPath("$.categories", not(hasItem("Home"))));       // fixture-only category is gone
        mvc.perform(get("/api/transactions")).andExpect(jsonPath("$.totalRows").value(200));
        mvc.perform(get("/api/transactions/ORD-1")).andExpect(status().isNotFound()); // fixture order is gone
        mvc.perform(get("/api/transactions/export"))
                .andExpect(content().string(containsString("ORD-10001,")))
                .andExpect(content().string(not(containsString("ORD-1,"))));
        mvc.perform(get("/api/dashboard").param("region", "West").param("category", "Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regions", hasSize(1)))
                .andExpect(jsonPath("$.categories", hasSize(1)));
    }
}
