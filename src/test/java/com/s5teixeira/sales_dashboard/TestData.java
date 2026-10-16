package com.s5teixeira.sales_dashboard;

import java.io.IOException;
import java.io.InputStream;

import com.s5teixeira.sales_dashboard.service.DataImportService;
import org.springframework.core.io.ClassPathResource;

public final class TestData {

    private TestData() {
    }

    /** Deletes whatever is stored and loads the small hand-calculated fixture (6 orders, 8 line items). */
    public static void loadSmallFixture(DataImportService importService) {
        importService.deleteDataset();
        try (InputStream in = new ClassPathResource("fixtures/small_sales.csv").getInputStream()) {
            importService.importCsv(in, "small_sales.csv");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
