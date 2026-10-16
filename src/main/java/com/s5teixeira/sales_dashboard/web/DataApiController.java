package com.s5teixeira.sales_dashboard.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import com.s5teixeira.sales_dashboard.dto.DataStatus;
import com.s5teixeira.sales_dashboard.dto.ImportResult;
import com.s5teixeira.sales_dashboard.importer.CsvImportException;
import com.s5teixeira.sales_dashboard.service.DataImportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Endpoints for the single active dataset. Imports return 409 Conflict if a dataset is already loaded. */
@RestController
@RequestMapping("/api/data")
public class DataApiController {

    private final DataImportService importService;

    public DataApiController(DataImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/status")
    public DataStatus status() {
        return importService.status();
    }

    @PostMapping("/sample")
    public ImportResult loadSample() {
        return importService.loadSampleData();
    }

    @PostMapping("/upload")
    public ImportResult upload(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        if (file.isEmpty()) {
            throw new CsvImportException(List.of("The uploaded file is empty"));
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new CsvImportException(List.of("Only .csv files are supported"));
        }
        try (InputStream in = file.getInputStream()) {
            return importService.importCsv(in, fileName);
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDataset() {
        importService.deleteDataset();
    }
}
