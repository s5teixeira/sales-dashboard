package com.s5teixeira.sales_dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import com.s5teixeira.sales_dashboard.service.DataImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts and stops the real application several times against a file-based H2 database in a
 * temporary folder, to check what survives a restart.
 */
class PersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void newDatabaseStartsEmptyAndLoadedOrDeletedStateSurvivesRestarts() {
        String url = "jdbc:h2:file:" + tempDir.resolve("salesdb").toAbsolutePath();

        // First start: brand-new database, no sample data loaded automatically.
        try (ConfigurableApplicationContext app = start(url)) {
            DataImportService importService = app.getBean(DataImportService.class);
            assertThat(importService.status().orders()).isZero();
            importService.loadSampleData();
        }
        assertThat(tempDir.resolve("salesdb.mv.db")).exists(); // proves the data was written to a file

        // Second start: the loaded dataset is still there.
        try (ConfigurableApplicationContext app = start(url)) {
            DataImportService importService = app.getBean(DataImportService.class);
            assertThat(importService.status().orders()).isEqualTo(200);
            importService.deleteDataset();
        }

        // Third start: after deleting, the database stays empty.
        try (ConfigurableApplicationContext app = start(url)) {
            assertThat(app.getBean(DataImportService.class).status().orders()).isZero();
        }
    }

    private static ConfigurableApplicationContext start(String databaseUrl) {
        // Command-line style arguments override the test application.properties.
        return new SpringApplicationBuilder(SalesDashboardApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + databaseUrl);
    }
}
