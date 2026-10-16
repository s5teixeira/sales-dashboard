package com.s5teixeira.sales_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.s5teixeira.sales_dashboard.TestData;
import com.s5teixeira.sales_dashboard.dto.ImportResult;
import com.s5teixeira.sales_dashboard.dto.Kpis;
import com.s5teixeira.sales_dashboard.importer.CsvImportException;
import com.s5teixeira.sales_dashboard.importer.DatasetAlreadyLoadedException;
import com.s5teixeira.sales_dashboard.model.SalesFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DataImportServiceTest {

    private static final String HEADER =
            "order_id,order_date,customer_id,region,status,line_item_id,product_name,category,quantity,unit_price,discount_percent\n";

    @Autowired
    DataImportService importService;

    @Autowired
    DashboardService dashboardService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sampleDatasetHasExactly200OrdersAndTheSameLineItemsAsTheCsv() throws Exception {
        importService.deleteDataset();
        ImportResult result = importService.loadSampleData();

        List<String[]> csvRows = readSampleCsvRows();
        Set<String> csvOrderIds = new HashSet<>();
        csvRows.forEach(row -> csvOrderIds.add(row[0]));

        assertThat(result.orders()).isEqualTo(200);
        assertThat(csvOrderIds).hasSize(200);
        assertThat(result.lineItems()).isEqualTo(csvRows.size()).isBetween(300, 350);
        assertThat(importService.status().orders()).isEqualTo(200);
        assertThat(importService.status().lineItems()).isEqualTo(csvRows.size());
    }

    @Test
    void dashboardMetricsMatchTheStoredRecords() throws Exception {
        importService.deleteDataset();
        importService.loadSampleData();
        Kpis kpis = dashboardService.dashboard(SalesFilter.none()).kpis();

        // Recalculate revenue independently in Java straight from the CSV file.
        BigDecimal expectedRevenue = BigDecimal.ZERO;
        for (String[] row : readSampleCsvRows()) {
            if (!row[4].equals("COMPLETED")) continue;
            BigDecimal line = new BigDecimal(row[8]).multiply(new BigDecimal(row[9]))
                    .multiply(new BigDecimal("100").subtract(new BigDecimal(row[10])))
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            expectedRevenue = expectedRevenue.add(line);
        }

        assertThat(kpis.totalRevenue()).isEqualByComparingTo(expectedRevenue);
        assertThat(kpis.totalOrders()).isEqualTo(count("SELECT COUNT(*) FROM orders"));
        assertThat(kpis.completedOrders()).isEqualTo(count("SELECT COUNT(*) FROM orders WHERE status = 'COMPLETED'"));
        assertThat(kpis.returnedOrders()).isEqualTo(count("SELECT COUNT(*) FROM orders WHERE status = 'RETURNED'"));
    }

    @Test
    void uploadingAValidCsvLoadsExactlyItsRecords() {
        importService.deleteDataset();
        ImportResult result = importService.importCsv(stream(HEADER
                + "A-1,2026-03-01,CUST-1,North,COMPLETED,A-1-1,Desk,Office,1,250.00,0\n"
                + "A-1,2026-03-01,CUST-1,North,COMPLETED,A-1-2,Lamp,Office,2,40.00,10\n"
                + "A-2,2026-03-05,CUST-2,South,RETURNED,A-2-1,Mug,Home,1,12.50,0\n"), "my_sales.csv");

        assertThat(result.orders()).isEqualTo(2);
        assertThat(result.lineItems()).isEqualTo(3);
        assertThat(importService.status().orders()).isEqualTo(2);
        assertThat(importService.status().lineItems()).isEqualTo(3);
        assertThat(dashboardService.dashboard(SalesFilter.none()).kpis().totalRevenue()).isEqualByComparingTo("322.00");
    }

    @Test
    void onlyOneDatasetCanBeActiveAtATime() {
        TestData.loadSmallFixture(importService);

        assertThatThrownBy(() -> importService.loadSampleData()).isInstanceOf(DatasetAlreadyLoadedException.class);
        assertThatThrownBy(() -> importService.importCsv(stream(HEADER
                + "B-1,2026-03-01,CUST-1,North,COMPLETED,B-1-1,Desk,Office,1,250.00,0\n"), "another.csv"))
                .isInstanceOf(DatasetAlreadyLoadedException.class);

        assertThat(importService.status().orders()).isEqualTo(6);
        assertThat(importService.status().lineItems()).isEqualTo(8);
    }

    @Test
    void invalidCsvIsRejectedWithoutWritingAnything() {
        importService.deleteDataset();
        // The first row is valid, the second is not: neither may be saved.
        String csv = HEADER
                + "C-1,2026-01-01,CUST-9,East,COMPLETED,C-1-1,Thing,Home,1,10.00,0\n"
                + "C-2,not-a-date,CUST-9,East,COMPLETED,C-2-1,Thing,Home,1,10.00,0\n";

        assertThatThrownBy(() -> importService.importCsv(stream(csv), "bad.csv"))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getErrors().getFirst()).contains("Line 3: order_date"));
        assertThat(importService.status().orders()).isZero();
        assertThat(importService.status().lineItems()).isZero();
    }

    @Test
    void invalidCsvDoesNotModifyAnExistingDataset() {
        TestData.loadSmallFixture(importService);
        assertThatThrownBy(() -> importService.importCsv(stream(HEADER + "broken row\n"), "bad.csv"))
                .isInstanceOf(RuntimeException.class);
        assertThat(importService.status().orders()).isEqualTo(6);
        assertThat(dashboardService.dashboard(SalesFilter.none()).kpis().totalRevenue()).isEqualByComparingTo("343.33");
    }

    @Test
    void deletingRemovesAllRowsAndANewDatasetCanBeLoadedRightAway() {
        TestData.loadSmallFixture(importService);

        importService.deleteDataset();
        assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
        assertThat(count("SELECT COUNT(*) FROM order_items")).isZero();

        // The tables still exist, so the next import works immediately.
        assertThat(importService.loadSampleData().orders()).isEqualTo(200);
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** Data rows of the sample CSV, split on commas (the sample has no quoted commas). */
    private static List<String[]> readSampleCsvRows() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("sample_data/sales_sample.csv").getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).filter(line -> !line.isBlank()).map(line -> line.split(",")).toList();
        }
    }

    private static InputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
