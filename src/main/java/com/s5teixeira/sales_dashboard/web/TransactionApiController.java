package com.s5teixeira.sales_dashboard.web;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.s5teixeira.sales_dashboard.dto.OrderDetail;
import com.s5teixeira.sales_dashboard.dto.TransactionPage;
import com.s5teixeira.sales_dashboard.model.TransactionQuery;
import com.s5teixeira.sales_dashboard.service.TransactionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/transactions")
public class TransactionApiController {

    private final TransactionService transactionService;

    public TransactionApiController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Paged order list. Query params: search, status, startDate, endDate, category, region,
     * sort (DATE_DESC | DATE_ASC | AMOUNT_DESC | AMOUNT_ASC), page (0-based), size (1-100).
     */
    @GetMapping
    public TransactionPage search(@Valid TransactionQuery query) {
        return transactionService.search(query);
    }

    @GetMapping("/{orderId}")
    public OrderDetail order(@PathVariable String orderId) {
        return transactionService.findOrder(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order " + orderId + " not found"));
    }

    /** Downloads all orders matching the same filters as CSV. */
    @GetMapping("/export")
    public void export(@Valid TransactionQuery query, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions.csv\"");
        Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
        transactionService.exportCsv(query, writer);
        writer.flush();
    }
}
