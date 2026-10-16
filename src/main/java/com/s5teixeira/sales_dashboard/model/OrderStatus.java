package com.s5teixeira.sales_dashboard.model;

/** Lifecycle status of an order. Only COMPLETED orders count toward recognized revenue. */
public enum OrderStatus {
    COMPLETED,
    CANCELLED,
    RETURNED
}
