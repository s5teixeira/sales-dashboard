package com.s5teixeira.sales_dashboard.repository;

/** Shared SQL expressions so every query applies the same business rules. */
final class SqlFragments {

    private SqlFragments() {
    }

    /**
     * Net revenue of one line item: quantity x unit price x (1 - discount / 100), rounded to cents.
     * Written as (100 - discount) / 100 so the arithmetic stays in exact DECIMAL.
     */
    static final String LINE_NET = "ROUND(oi.quantity * oi.unit_price * (100 - oi.discount_percent) / 100, 2)";

    /** Line value before discount. */
    static final String LINE_GROSS = "(oi.quantity * oi.unit_price)";

    static final String ORDERS_JOIN_ITEMS = " FROM orders o JOIN order_items oi ON oi.order_id = o.order_id";

    /** First day of the order's month, as a DATE. */
    static final String ORDER_MONTH = "DATEADD(DAY, 1 - DAY(o.order_date), o.order_date)";
}
