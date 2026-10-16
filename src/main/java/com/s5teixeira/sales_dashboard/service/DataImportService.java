package com.s5teixeira.sales_dashboard.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.s5teixeira.sales_dashboard.dto.DataStatus;
import com.s5teixeira.sales_dashboard.dto.ImportResult;
import com.s5teixeira.sales_dashboard.importer.CsvImportException;
import com.s5teixeira.sales_dashboard.importer.DatasetAlreadyLoadedException;
import com.s5teixeira.sales_dashboard.importer.SalesCsvParser;
import com.s5teixeira.sales_dashboard.importer.SalesDataset;
import com.s5teixeira.sales_dashboard.repository.SalesDataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the single active dataset: load the sample, upload a CSV, or delete what is stored.
 *
 * The app works with one dataset at a time. An import is only allowed when the database is empty;
 * to switch datasets the user deletes the current one first.
 *
 * Each public method is @Transactional, so its database work either fully succeeds or is fully
 * rolled back. For example, if inserting line item 150 of 300 fails, the orders and line items
 * inserted before it are undone too, and the database is left exactly as it was.
 */
@Service
public class DataImportService {

    private final SalesCsvParser parser;
    private final SalesDataRepository repository;
    private final Resource sampleData;

    public DataImportService(SalesCsvParser parser, SalesDataRepository repository,
                             @Value("${app.sample-data.location}") Resource sampleData) {
        this.parser = parser;
        this.repository = repository;
        this.sampleData = sampleData;
    }

    @Transactional
    public ImportResult loadSampleData() {
        try (InputStream in = sampleData.getInputStream()) {
            return importCsv(in, "Sample data");
        } catch (IOException e) {
            throw new CsvImportException(List.of("Sample data file could not be read: " + e.getMessage()));
        }
    }

    /**
     * Validates the whole CSV first, then inserts it. Nothing is written if the file has any
     * invalid row. The uploaded file itself is only read, never changed.
     */
    @Transactional
    public ImportResult importCsv(InputStream in, String source) {
        if (repository.hasOrders()) {
            throw new DatasetAlreadyLoadedException();
        }

        SalesDataset dataset = parse(in);
        repository.insertOrders(dataset.orders());
        repository.insertLineItems(dataset.lineItems());
        return new ImportResult(source, dataset.orders().size(), dataset.lineItems().size());
    }

    /**
     * Deletes every order and line item in one transaction, so the database is never left with
     * line items whose order is gone. The schema stays, and the empty state is saved to the H2 file,
     * so after a restart the app still starts empty until a new dataset is loaded.
     */
    @Transactional
    public void deleteDataset() {
        repository.deleteAllRows();
    }

    public DataStatus status() {
        return repository.status();
    }

    private SalesDataset parse(InputStream in) {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parser.parse(reader);
        } catch (IOException e) {
            throw new CsvImportException(List.of("File could not be read: " + e.getMessage()));
        }
    }
}
