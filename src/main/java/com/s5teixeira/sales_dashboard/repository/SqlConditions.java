package com.s5teixeira.sales_dashboard.repository;

import java.util.ArrayList;
import java.util.List;

import com.s5teixeira.sales_dashboard.model.SalesFilter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Builds a WHERE clause from constant SQL fragments plus named parameters.
 * Only the fragments written in this codebase become SQL text; every user-supplied
 * value is bound as a parameter, which prevents SQL injection.
 */
final class SqlConditions {

    private final List<String> clauses = new ArrayList<>();
    private final MapSqlParameterSource params = new MapSqlParameterSource();

    /**
     * Filters at the line-item level (requires aliases {@code o} = orders and {@code oi} = order_items).
     * With a category filter, only line items in that category are included in the result.
     */
    static SqlConditions forLineItems(SalesFilter filter) {
        SqlConditions c = forDatesAndRegion(filter);
        if (filter.category() != null) {
            c.add("oi.category = :category", "category", filter.category());
        }
        return c;
    }

    /**
     * Filters at the order level (alias {@code o} only). A category filter keeps orders that contain
     * at least one item in that category.
     */
    static SqlConditions forOrders(SalesFilter filter) {
        SqlConditions c = forDatesAndRegion(filter);
        if (filter.category() != null) {
            c.add("EXISTS (SELECT 1 FROM order_items ci WHERE ci.order_id = o.order_id AND ci.category = :category)",
                    "category", filter.category());
        }
        return c;
    }

    private static SqlConditions forDatesAndRegion(SalesFilter filter) {
        SqlConditions c = new SqlConditions();
        if (filter.startDate() != null) c.add("o.order_date >= :startDate", "startDate", filter.startDate());
        if (filter.endDate() != null) c.add("o.order_date <= :endDate", "endDate", filter.endDate());
        if (filter.region() != null) c.add("o.region = :region", "region", filter.region());
        return c;
    }

    SqlConditions add(String clause, String paramName, Object value) {
        clauses.add(clause);
        params.addValue(paramName, value);
        return this;
    }

    /** Adds a constant clause that has no parameters. */
    SqlConditions add(String clause) {
        clauses.add(clause);
        return this;
    }

    SqlConditions param(String name, Object value) {
        params.addValue(name, value);
        return this;
    }

    String where() {
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    MapSqlParameterSource params() {
        return params;
    }
}
