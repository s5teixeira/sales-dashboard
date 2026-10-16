package com.s5teixeira.sales_dashboard.repository;

import static com.s5teixeira.sales_dashboard.repository.SqlFragments.LINE_NET;
import static com.s5teixeira.sales_dashboard.repository.SqlFragments.ORDERS_JOIN_ITEMS;
import static com.s5teixeira.sales_dashboard.repository.SqlFragments.ORDER_MONTH;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.s5teixeira.sales_dashboard.dto.CategoryRevenue;
import com.s5teixeira.sales_dashboard.dto.FilterOptions;
import com.s5teixeira.sales_dashboard.dto.Kpis;
import com.s5teixeira.sales_dashboard.dto.MonthlyRevenue;
import com.s5teixeira.sales_dashboard.dto.ProductRevenue;
import com.s5teixeira.sales_dashboard.dto.RegionPerformance;
import com.s5teixeira.sales_dashboard.dto.StatusBreakdown;
import com.s5teixeira.sales_dashboard.model.SalesFilter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * All dashboard analytics, calculated in SQL.
 *
 * <p>Business rules applied consistently:
 * <ul>
 *   <li>Line revenue = quantity x unit_price x (1 - discount_percent / 100), rounded to cents.</li>
 *   <li>Recognized revenue, units and order counts use COMPLETED orders only.</li>
 *   <li>Orders are always counted with COUNT(DISTINCT order_id), because the join to order_items
 *       produces one row per line item.</li>
 *   <li>With a category filter, only line items in that category are measured, and an order is counted
 *       if it contains at least one item in the category.</li>
 *   <li>Ratios use NULLIF(denominator, 0) so a zero denominator yields NULL ("N/A"), not 0 or an error.</li>
 * </ul>
 */
@Repository
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Kpis kpis(SalesFilter filter) {
        SqlConditions c = SqlConditions.forLineItems(filter);
        String sql = """
                WITH filtered AS (
                    SELECT o.order_id, o.customer_id, o.status, oi.quantity, %s AS line_net
                    %s %s
                ),
                totals AS (
                    SELECT
                        COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN line_net END), 0)  AS total_revenue,
                        COUNT(DISTINCT CASE WHEN status = 'COMPLETED' THEN order_id END)    AS completed_orders,
                        COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN quantity END), 0)  AS units_sold,
                        COUNT(DISTINCT CASE WHEN status = 'RETURNED' THEN order_id END)     AS returned_orders,
                        COUNT(DISTINCT CASE WHEN status = 'CANCELLED' THEN order_id END)    AS cancelled_orders,
                        COUNT(DISTINCT CASE WHEN status <> 'CANCELLED' THEN order_id END)   AS non_cancelled_orders,
                        COUNT(DISTINCT order_id)                                            AS total_orders,
                        COUNT(DISTINCT CASE WHEN status = 'COMPLETED' THEN customer_id END) AS unique_customers
                    FROM filtered
                )
                SELECT t.*,
                       CAST(ROUND(t.total_revenue / NULLIF(t.completed_orders, 0), 2) AS DECIMAL(14, 2)) AS avg_order_value,
                       CAST(ROUND(CAST(t.returned_orders AS DECIMAL(19, 6)) * 100
                                  / NULLIF(t.non_cancelled_orders, 0), 2) AS DECIMAL(7, 2))          AS return_rate_pct
                FROM totals t
                """.formatted(LINE_NET, ORDERS_JOIN_ITEMS, c.where());

        return jdbc.queryForObject(sql, c.params(), (rs, n) -> new Kpis(
                money(rs.getBigDecimal("total_revenue")),
                rs.getLong("completed_orders"),
                rs.getBigDecimal("avg_order_value"),
                rs.getLong("units_sold"),
                rs.getLong("returned_orders"),
                rs.getLong("cancelled_orders"),
                rs.getLong("non_cancelled_orders"),
                rs.getLong("total_orders"),
                rs.getBigDecimal("return_rate_pct"),
                rs.getLong("unique_customers"),
                null));
    }

    /**
     * Completed revenue per calendar month between firstMonth and lastMonth (inclusive, both
     * first-of-month dates). Months with no sales are returned as zero rather than skipped, so
     * LAG() always compares a month with the calendar month directly before it.
     */
    public List<MonthlyRevenue> monthlyRevenue(SalesFilter filter, LocalDate firstMonth, LocalDate lastMonth) {
        long span = ChronoUnit.MONTHS.between(firstMonth, lastMonth);
        SqlConditions c = SqlConditions.forLineItems(filter)
                .add("o.status = 'COMPLETED'")
                .param("firstMonth", firstMonth)
                .param("span", span);
        String sql = """
                WITH months AS (
                    SELECT DATEADD(MONTH, X, CAST(:firstMonth AS DATE)) AS month_start
                    FROM SYSTEM_RANGE(0, :span)
                ),
                monthly AS (
                    SELECT %1$s AS month_start,
                           SUM(%2$s)                 AS revenue,
                           COUNT(DISTINCT o.order_id) AS completed_orders,
                           SUM(oi.quantity)           AS units_sold
                    %3$s %4$s
                    GROUP BY %1$s
                ),
                series AS (
                    SELECT m.month_start,
                           COALESCE(s.revenue, 0)          AS revenue,
                           COALESCE(s.completed_orders, 0) AS completed_orders,
                           COALESCE(s.units_sold, 0)       AS units_sold
                    FROM months m
                    LEFT JOIN monthly s ON s.month_start = m.month_start
                )
                SELECT month_start, revenue, completed_orders, units_sold, previous_revenue,
                       CAST(ROUND((revenue - previous_revenue) * 100 / NULLIF(previous_revenue, 0), 2)
                            AS DECIMAL(9, 2)) AS growth_pct
                FROM (
                    SELECT series.*, LAG(revenue) OVER (ORDER BY month_start) AS previous_revenue
                    FROM series
                ) x
                ORDER BY month_start
                """.formatted(ORDER_MONTH, LINE_NET, ORDERS_JOIN_ITEMS, c.where());

        return jdbc.query(sql, c.params(), (rs, n) -> new MonthlyRevenue(
                rs.getObject("month_start", LocalDate.class),
                money(rs.getBigDecimal("revenue")),
                rs.getLong("completed_orders"),
                rs.getLong("units_sold"),
                money(rs.getBigDecimal("previous_revenue")),
                rs.getBigDecimal("growth_pct")));
    }

    public List<CategoryRevenue> revenueByCategory(SalesFilter filter) {
        SqlConditions c = SqlConditions.forLineItems(filter).add("o.status = 'COMPLETED'");
        String sql = """
                SELECT oi.category,
                       SUM(%1$s)                  AS revenue,
                       SUM(oi.quantity)           AS units_sold,
                       COUNT(DISTINCT o.order_id) AS orders,
                       CAST(ROUND(SUM(%1$s) * 100 / NULLIF(SUM(SUM(%1$s)) OVER (), 0), 2) AS DECIMAL(7, 2)) AS share_pct
                %2$s %3$s
                GROUP BY oi.category
                ORDER BY revenue DESC, oi.category
                """.formatted(LINE_NET, ORDERS_JOIN_ITEMS, c.where());
        return jdbc.query(sql, c.params(), (rs, n) -> new CategoryRevenue(
                rs.getString("category"),
                money(rs.getBigDecimal("revenue")),
                rs.getLong("units_sold"),
                rs.getLong("orders"),
                rs.getBigDecimal("share_pct")));
    }

    public List<ProductRevenue> topProducts(SalesFilter filter, int limit) {
        SqlConditions c = SqlConditions.forLineItems(filter)
                .add("o.status = 'COMPLETED'")
                .param("limit", limit);
        String sql = """
                SELECT oi.product_name, oi.category,
                       SUM(%s)                    AS revenue,
                       SUM(oi.quantity)           AS units_sold,
                       COUNT(DISTINCT o.order_id) AS orders
                %s %s
                GROUP BY oi.product_name, oi.category
                ORDER BY revenue DESC, oi.product_name
                LIMIT :limit
                """.formatted(LINE_NET, ORDERS_JOIN_ITEMS, c.where());
        return jdbc.query(sql, c.params(), (rs, n) -> new ProductRevenue(
                rs.getString("product_name"),
                rs.getString("category"),
                money(rs.getBigDecimal("revenue")),
                rs.getLong("units_sold"),
                rs.getLong("orders")));
    }

    public List<RegionPerformance> performanceByRegion(SalesFilter filter) {
        SqlConditions c = SqlConditions.forLineItems(filter);
        String sql = """
                WITH by_region AS (
                    SELECT o.region,
                           COALESCE(SUM(CASE WHEN o.status = 'COMPLETED' THEN %s END), 0)    AS revenue,
                           COUNT(DISTINCT CASE WHEN o.status = 'COMPLETED' THEN o.order_id END)  AS completed_orders,
                           COUNT(DISTINCT CASE WHEN o.status = 'RETURNED' THEN o.order_id END)   AS returned_orders,
                           COUNT(DISTINCT CASE WHEN o.status <> 'CANCELLED' THEN o.order_id END) AS non_cancelled_orders
                    %s %s
                    GROUP BY o.region
                )
                SELECT region, revenue, completed_orders,
                       CAST(ROUND(revenue / NULLIF(completed_orders, 0), 2) AS DECIMAL(14, 2)) AS avg_order_value,
                       CAST(ROUND(CAST(returned_orders AS DECIMAL(19, 6)) * 100 / NULLIF(non_cancelled_orders, 0), 2)
                            AS DECIMAL(7, 2)) AS return_rate_pct,
                       CAST(ROUND(revenue * 100 / NULLIF(SUM(revenue) OVER (), 0), 2) AS DECIMAL(7, 2)) AS share_pct
                FROM by_region
                ORDER BY revenue DESC, region
                """.formatted(LINE_NET, ORDERS_JOIN_ITEMS, c.where());
        return jdbc.query(sql, c.params(), (rs, n) -> new RegionPerformance(
                rs.getString("region"),
                money(rs.getBigDecimal("revenue")),
                rs.getLong("completed_orders"),
                rs.getBigDecimal("avg_order_value"),
                rs.getBigDecimal("return_rate_pct"),
                rs.getBigDecimal("share_pct")));
    }

    public List<StatusBreakdown> statusBreakdown(SalesFilter filter) {
        SqlConditions c = SqlConditions.forLineItems(filter);
        String sql = """
                SELECT o.status,
                       COUNT(DISTINCT o.order_id) AS orders,
                       SUM(%s)                    AS order_value,
                       CAST(ROUND(CAST(COUNT(DISTINCT o.order_id) AS DECIMAL(19, 6)) * 100
                                  / NULLIF(SUM(COUNT(DISTINCT o.order_id)) OVER (), 0), 2) AS DECIMAL(7, 2)) AS share_pct
                %s %s
                GROUP BY o.status
                ORDER BY CASE o.status WHEN 'COMPLETED' THEN 1 WHEN 'RETURNED' THEN 2 ELSE 3 END
                """.formatted(LINE_NET, ORDERS_JOIN_ITEMS, c.where());
        return jdbc.query(sql, c.params(), (rs, n) -> new StatusBreakdown(
                rs.getString("status"),
                rs.getLong("orders"),
                money(rs.getBigDecimal("order_value")),
                rs.getBigDecimal("share_pct")));
    }

    public FilterOptions filterOptions() {
        var jt = jdbc.getJdbcTemplate();
        List<String> categories = jt.queryForList("SELECT DISTINCT category FROM order_items ORDER BY category", String.class);
        List<String> regions = jt.queryForList("SELECT DISTINCT region FROM orders ORDER BY region", String.class);
        return jt.queryForObject("SELECT MIN(order_date) AS min_date, MAX(order_date) AS max_date FROM orders",
                (rs, n) -> new FilterOptions(categories, regions,
                        rs.getObject("min_date", LocalDate.class),
                        rs.getObject("max_date", LocalDate.class)));
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
