package com.s5teixeira.sales_dashboard.model;

/**
 * Whitelisted sort orders for the transaction explorer. The ORDER BY fragment is a
 * constant, so user input never reaches the SQL text.
 */
public enum TransactionSort {
    DATE_DESC("o.order_date DESC, o.order_id DESC"),
    DATE_ASC("o.order_date ASC, o.order_id ASC"),
    AMOUNT_DESC("net_amount DESC, o.order_id ASC"),
    AMOUNT_ASC("net_amount ASC, o.order_id ASC");

    private final String orderBy;

    TransactionSort(String orderBy) {
        this.orderBy = orderBy;
    }

    public String orderBy() {
        return orderBy;
    }
}
