package com.s5teixeira.sales_dashboard.importer;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.s5teixeira.sales_dashboard.importer.SalesDataset.LineItemRecord;
import com.s5teixeira.sales_dashboard.importer.SalesDataset.OrderRecord;
import com.s5teixeira.sales_dashboard.model.OrderStatus;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parses and validates the flat sales CSV (one row per line item, order columns repeated).
 * All rows are validated before anything is returned, so an import is all-or-nothing.
 */
@Component
public class SalesCsvParser {

    static final List<String> REQUIRED_COLUMNS = List.of(
            "order_id", "order_date", "customer_id", "region", "status",
            "line_item_id", "product_name", "category", "quantity", "unit_price", "discount_percent");

    private static final int MAX_REPORTED_ERRORS = 25;
    private static final int MAX_ID_LENGTH = 20;
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public SalesDataset parse(Reader reader) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();

        List<String> errors = new ArrayList<>();
        Map<String, OrderRecord> orders = new LinkedHashMap<>();
        List<LineItemRecord> items = new ArrayList<>();
        Set<String> lineIds = new HashSet<>();

        try (CSVParser parser = format.parse(reader)) {
            List<String> header = parser.getHeaderNames().stream()
                    .map(h -> h.trim().toLowerCase(Locale.ROOT).replace("﻿", ""))
                    .toList();
            List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !header.contains(c)).toList();
            if (!missing.isEmpty()) {
                throw new CsvImportException(List.of("Missing required column(s): " + String.join(", ", missing)));
            }
            Map<String, String> columnLookup = new LinkedHashMap<>();
            for (String raw : parser.getHeaderNames()) {
                columnLookup.put(raw.trim().toLowerCase(Locale.ROOT).replace("﻿", ""), raw);
            }

            for (CSVRecord record : parser) {
                long line = record.getRecordNumber() + 1; // +1 for the header row
                RowReader row = new RowReader(record, columnLookup, line, errors);
                int errorsBefore = errors.size();

                String orderId = row.id("order_id");
                LocalDate orderDate = row.date("order_date");
                String customerId = row.id("customer_id");
                String region = row.text("region", 40);
                OrderStatus status = row.status("status");
                String lineItemId = row.id("line_item_id");
                String productName = row.text("product_name", 120);
                String category = row.text("category", 60);
                Integer quantity = row.positiveInt("quantity");
                BigDecimal unitPrice = row.money("unit_price");
                BigDecimal discount = row.percent("discount_percent");

                if (errors.size() > errorsBefore) {
                    if (errors.size() >= MAX_REPORTED_ERRORS) break;
                    continue;
                }

                if (!lineIds.add(lineItemId)) {
                    errors.add("Line %d: duplicate line_item_id '%s'".formatted(line, lineItemId));
                    continue;
                }

                OrderRecord order = new OrderRecord(orderId, orderDate, customerId, region, status);
                OrderRecord existing = orders.putIfAbsent(orderId, order);
                if (existing != null && !existing.equals(order)) {
                    errors.add(("Line %d: order '%s' has different date/customer/region/status than an earlier row "
                            + "for the same order").formatted(line, orderId));
                    continue;
                }
                items.add(new LineItemRecord(lineItemId, orderId, productName, category, quantity, unitPrice, discount));
            }
        } catch (IllegalStateException | IllegalArgumentException | java.io.UncheckedIOException e) {
            throw new CsvImportException(List.of("The file could not be read as CSV: " + e.getMessage()));
        }

        if (errors.isEmpty() && items.isEmpty()) {
            errors.add("The file contains no data rows");
        }
        if (!errors.isEmpty()) {
            throw new CsvImportException(errors.size() > MAX_REPORTED_ERRORS ? errors.subList(0, MAX_REPORTED_ERRORS) : errors);
        }
        return new SalesDataset(List.copyOf(orders.values()), List.copyOf(items));
    }

    /** Reads typed values from one CSV row, recording a readable error for each invalid field. */
    private record RowReader(CSVRecord record, Map<String, String> columns, long line, List<String> errors) {

        String raw(String column) {
            String value = record.isMapped(columns.get(column)) && record.isSet(columns.get(column))
                    ? record.get(columns.get(column)) : null;
            return value == null ? "" : value.trim();
        }

        String text(String column, int maxLength) {
            String value = raw(column);
            if (value.isEmpty()) {
                error(column, "is required");
                return null;
            }
            if (value.length() > maxLength) {
                error(column, "is longer than " + maxLength + " characters");
                return null;
            }
            return value;
        }

        String id(String column) {
            String value = text(column, MAX_ID_LENGTH);
            return value == null ? null : value.toUpperCase(Locale.ROOT);
        }

        LocalDate date(String column) {
            String value = raw(column);
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                error(column, "must be a date in YYYY-MM-DD format (got '" + value + "')");
                return null;
            }
        }

        OrderStatus status(String column) {
            String value = raw(column).toUpperCase(Locale.ROOT);
            try {
                return OrderStatus.valueOf(value);
            } catch (IllegalArgumentException e) {
                error(column, "must be COMPLETED, CANCELLED or RETURNED (got '" + raw(column) + "')");
                return null;
            }
        }

        Integer positiveInt(String column) {
            String value = raw(column);
            try {
                int n = Integer.parseInt(value);
                if (n > 0) return n;
            } catch (NumberFormatException ignored) {
                // reported below
            }
            error(column, "must be a whole number greater than 0 (got '" + value + "')");
            return null;
        }

        BigDecimal money(String column) {
            BigDecimal value = decimal(column);
            if (value == null) return null;
            if (value.signum() < 0 || value.compareTo(MAX_PRICE) > 0 || value.stripTrailingZeros().scale() > 2) {
                error(column, "must be a non-negative amount with at most 2 decimal places (got '" + raw(column) + "')");
                return null;
            }
            return value.setScale(2);
        }

        BigDecimal percent(String column) {
            if (raw(column).isEmpty()) return BigDecimal.ZERO.setScale(2); // optional: no discount
            BigDecimal value = decimal(column);
            if (value == null) return null;
            if (value.signum() < 0 || value.compareTo(HUNDRED) > 0 || value.stripTrailingZeros().scale() > 2) {
                error(column, "must be between 0 and 100 with at most 2 decimal places (got '" + raw(column) + "')");
                return null;
            }
            return value.setScale(2);
        }

        private BigDecimal decimal(String column) {
            String value = raw(column).replace("$", "").replace("%", "");
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                error(column, "must be a number (got '" + raw(column) + "')");
                return null;
            }
        }

        private void error(String column, String message) {
            errors.add("Line %d: %s %s".formatted(line, column, message));
        }
    }
}
