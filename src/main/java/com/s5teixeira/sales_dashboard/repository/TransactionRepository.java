package com.s5teixeira.sales_dashboard.repository;

import static com.s5teixeira.sales_dashboard.repository.SqlFragments.LINE_GROSS;
import static com.s5teixeira.sales_dashboard.repository.SqlFragments.LINE_NET;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.s5teixeira.sales_dashboard.dto.LineItem;
import com.s5teixeira.sales_dashboard.dto.TransactionRow;
import com.s5teixeira.sales_dashboard.model.SalesFilter;
import com.s5teixeira.sales_dashboard.model.TransactionQuery;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Order-level queries for the transaction explorer. Each row is one order with totals across all
 * of its line items. A category filter keeps orders that contain at least one item in that category.
 */
@Repository
public class TransactionRepository {

    private static final String ORDER_COLUMNS = """
            SELECT o.order_id, o.order_date, o.customer_id, o.region, o.status,
                   COUNT(oi.line_item_id)                                               AS item_count,
                   SUM(oi.quantity)                                                     AS units,
                   LISTAGG(DISTINCT oi.category, ', ') WITHIN GROUP (ORDER BY oi.category) AS categories,
                   SUM(%1$s)                                                            AS gross_amount,
                   SUM(%2$s)                                                            AS net_amount
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.order_id
            """.formatted(LINE_GROSS, LINE_NET);

    private static final String GROUP_BY = " GROUP BY o.order_id, o.order_date, o.customer_id, o.region, o.status ";

    private final NamedParameterJdbcTemplate jdbc;

    public TransactionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(TransactionQuery query) {
        SqlConditions c = conditions(query);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM orders o" + c.where(), c.params(), Long.class);
        return count == null ? 0 : count;
    }

    public List<TransactionRow> findPage(TransactionQuery query) {
        SqlConditions c = conditions(query)
                .param("limit", query.size())
                .param("offset", (long) query.page() * query.size());
        String sql = ORDER_COLUMNS + c.where() + GROUP_BY
                + " ORDER BY " + query.sort().orderBy() // constant from a whitelisted enum
                + " LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, c.params(), (rs, n) -> mapRow(rs));
    }

    /** Streams every matching order (no paging), used for CSV export. */
    public void forEach(TransactionQuery query, Consumer<TransactionRow> consumer) {
        SqlConditions c = conditions(query);
        String sql = ORDER_COLUMNS + c.where() + GROUP_BY + " ORDER BY " + query.sort().orderBy();
        jdbc.query(sql, c.params(), (RowCallbackHandler) rs -> consumer.accept(mapRow(rs)));
    }

    public Optional<TransactionRow> findOrder(String orderId) {
        SqlConditions c = new SqlConditions().add("o.order_id = :orderId", "orderId", orderId);
        List<TransactionRow> rows = jdbc.query(ORDER_COLUMNS + c.where() + GROUP_BY, c.params(), (rs, n) -> mapRow(rs));
        return rows.stream().findFirst();
    }

    public List<LineItem> findLineItems(String orderId) {
        String sql = """
                SELECT oi.line_item_id, oi.product_name, oi.category, oi.quantity, oi.unit_price,
                       oi.discount_percent, %s AS line_net
                FROM order_items oi
                WHERE oi.order_id = :orderId
                ORDER BY oi.line_item_id
                """.formatted(LINE_NET);
        return jdbc.query(sql, java.util.Map.of("orderId", orderId), (rs, n) -> new LineItem(
                rs.getString("line_item_id"),
                rs.getString("product_name"),
                rs.getString("category"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("discount_percent"),
                money(rs.getBigDecimal("line_net"))));
    }

    private static SqlConditions conditions(TransactionQuery q) {
        SqlConditions c = SqlConditions.forOrders(new SalesFilter(q.startDate(), q.endDate(), q.category(), q.region()));
        if (q.status() != null) {
            c.add("o.status = :status", "status", q.status().name());
        }
        if (q.search() != null) {
            // Partial, case-insensitive match on order ID or customer ID. LIKE wildcards typed by
            // the user are escaped so they are matched literally.
            String pattern = "%" + escapeLike(q.search().toUpperCase(Locale.ROOT)) + "%";
            c.add("(UPPER(o.order_id) LIKE :search ESCAPE '\\' OR UPPER(o.customer_id) LIKE :search ESCAPE '\\')",
                    "search", pattern);
        }
        return c;
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static TransactionRow mapRow(ResultSet rs) throws SQLException {
        BigDecimal gross = money(rs.getBigDecimal("gross_amount"));
        BigDecimal net = money(rs.getBigDecimal("net_amount"));
        return new TransactionRow(
                rs.getString("order_id"),
                rs.getObject("order_date", LocalDate.class),
                rs.getString("customer_id"),
                rs.getString("region"),
                rs.getString("status"),
                rs.getInt("item_count"),
                rs.getLong("units"),
                rs.getString("categories"),
                gross,
                gross.subtract(net),
                net);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
