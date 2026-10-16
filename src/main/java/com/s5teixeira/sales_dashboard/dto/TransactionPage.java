package com.s5teixeira.sales_dashboard.dto;

import java.util.List;

public record TransactionPage(
        List<TransactionRow> rows,
        int page,
        int size,
        long totalRows,
        int totalPages) {
}
