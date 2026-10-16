package com.s5teixeira.sales_dashboard.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.s5teixeira.sales_dashboard.model.OrderStatus;

/** A validated CSV file, split into the two normalized tables. */
public record SalesDataset(List<OrderRecord> orders, List<LineItemRecord> lineItems) {

    public record OrderRecord(String orderId, LocalDate orderDate, String customerId, String region,
                              OrderStatus status) {
    }

    public record LineItemRecord(String lineItemId, String orderId, String productName, String category,
                                 int quantity, BigDecimal unitPrice, BigDecimal discountPercent) {
    }
}
