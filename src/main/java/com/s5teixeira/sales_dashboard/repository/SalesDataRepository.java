package com.s5teixeira.sales_dashboard.repository;

import java.time.LocalDate;
import java.util.List;

import com.s5teixeira.sales_dashboard.dto.DataStatus;
import com.s5teixeira.sales_dashboard.importer.SalesDataset.LineItemRecord;
import com.s5teixeira.sales_dashboard.importer.SalesDataset.OrderRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/** Inserts and deletes the stored dataset, and reports what is currently stored. */
@Repository
public class SalesDataRepository {

    private static final String INSERT_ORDER = """
            INSERT INTO orders (order_id, order_date, customer_id, region, status)
            VALUES (:orderId, :orderDate, :customerId, :region, :status)""";

    private static final String INSERT_ITEM = """
            INSERT INTO order_items (line_item_id, order_id, product_name, category, quantity, unit_price, discount_percent)
            VALUES (:lineItemId, :orderId, :productName, :category, :quantity, :unitPrice, :discountPercent)""";

    private final NamedParameterJdbcTemplate jdbc;

    public SalesDataRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Removes every row from both tables. Only the data is deleted: the tables, keys and indexes
     * stay in place, so the app can import a new dataset straight away.
     * Line items are deleted first because they reference orders through a foreign key.
     */
    public void deleteAllRows() {
        jdbc.getJdbcTemplate().update("DELETE FROM order_items");
        jdbc.getJdbcTemplate().update("DELETE FROM orders");
    }

    public boolean hasOrders() {
        Integer count = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        return count != null && count > 0;
    }

    // Batch inserts send all rows in one round trip instead of one statement per row.
    public void insertOrders(List<OrderRecord> orders) {
        SqlParameterSource[] rows = orders.stream()
                .map(order -> new MapSqlParameterSource()
                        .addValue("orderId", order.orderId())
                        .addValue("orderDate", order.orderDate())
                        .addValue("customerId", order.customerId())
                        .addValue("region", order.region())
                        .addValue("status", order.status().name()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_ORDER, rows);
    }

    public void insertLineItems(List<LineItemRecord> items) {
        SqlParameterSource[] rows = items.stream()
                .map(item -> new MapSqlParameterSource()
                        .addValue("lineItemId", item.lineItemId())
                        .addValue("orderId", item.orderId())
                        .addValue("productName", item.productName())
                        .addValue("category", item.category())
                        .addValue("quantity", item.quantity())
                        .addValue("unitPrice", item.unitPrice())
                        .addValue("discountPercent", item.discountPercent()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(INSERT_ITEM, rows);
    }

    public DataStatus status() {
        return jdbc.getJdbcTemplate().queryForObject("""
                SELECT (SELECT COUNT(*) FROM orders)                      AS orders,
                       (SELECT COUNT(*) FROM order_items)                 AS line_items,
                       (SELECT COUNT(DISTINCT customer_id) FROM orders)   AS customers,
                       (SELECT MIN(order_date) FROM orders)               AS min_date,
                       (SELECT MAX(order_date) FROM orders)               AS max_date
                """, (rs, n) -> new DataStatus(
                rs.getLong("orders"),
                rs.getLong("line_items"),
                rs.getLong("customers"),
                rs.getObject("min_date", LocalDate.class),
                rs.getObject("max_date", LocalDate.class)));
    }
}
