package com.s5teixeira.sales_dashboard.dto;

import java.util.List;

public record OrderDetail(
        TransactionRow order,
        List<LineItem> items) {
}
