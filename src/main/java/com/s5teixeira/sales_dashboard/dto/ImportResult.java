package com.s5teixeira.sales_dashboard.dto;

/**
 * Outcome of a successful import.
 *
 * @param source     where the data came from (sample file or uploaded file name)
 * @param orders     number of orders imported
 * @param lineItems  number of line items imported
 */
public record ImportResult(String source, int orders, int lineItems) {
}
