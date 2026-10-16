package com.s5teixeira.sales_dashboard.service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.s5teixeira.sales_dashboard.dto.OrderDetail;
import com.s5teixeira.sales_dashboard.dto.TransactionPage;
import com.s5teixeira.sales_dashboard.dto.TransactionRow;
import com.s5teixeira.sales_dashboard.model.TransactionQuery;
import com.s5teixeira.sales_dashboard.repository.TransactionRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TransactionPage search(TransactionQuery query) {
        long total = repository.count(query);
        List<TransactionRow> rows = repository.findPage(query);
        int totalPages = (int) Math.ceil((double) total / query.size());
        return new TransactionPage(rows, query.page(), query.size(), total, totalPages);
    }

    @Transactional(readOnly = true)
    public Optional<OrderDetail> findOrder(String orderId) {
        String id = orderId.trim().toUpperCase(Locale.ROOT);
        return repository.findOrder(id).map(order -> new OrderDetail(order, repository.findLineItems(id)));
    }

    /** Writes every order matching the query (ignoring paging) as CSV. */
    @Transactional(readOnly = true)
    public void exportCsv(TransactionQuery query, Writer writer) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("order_id", "order_date", "customer_id", "region", "status", "line_items", "units",
                        "categories", "gross_amount", "discount_amount", "order_amount")
                .get();
        // Not try-with-resources: the caller owns the writer, so it is flushed but not closed here.
        CSVPrinter printer = new CSVPrinter(writer, format);
        IOException[] failure = new IOException[1];
        repository.forEach(query, row -> {
            if (failure[0] != null) return;
            try {
                printer.printRecord(row.orderId(), row.orderDate(), row.customerId(), row.region(), row.status(),
                        row.itemCount(), row.units(), row.categories(), row.grossAmount(),
                        row.discountAmount(), row.netAmount());
            } catch (IOException e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) throw failure[0];
        printer.flush();
    }
}
