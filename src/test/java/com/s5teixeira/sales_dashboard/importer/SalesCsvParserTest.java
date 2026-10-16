package com.s5teixeira.sales_dashboard.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.math.BigDecimal;

import com.s5teixeira.sales_dashboard.model.OrderStatus;
import org.junit.jupiter.api.Test;

class SalesCsvParserTest {

    private static final String HEADER =
            "order_id,order_date,customer_id,region,status,line_item_id,product_name,category,quantity,unit_price,discount_percent\n";

    private final SalesCsvParser parser = new SalesCsvParser();

    @Test
    void parsesRowsIntoOrdersAndLineItems() throws Exception {
        SalesDataset dataset = parser.parse(new StringReader(HEADER
                + "ORD-1,2026-01-10,C1,East,completed,L1,Widget,Electronics,2,100.00,10\n"
                + "ORD-1,2026-01-10,C1,East,COMPLETED,L2,\"Mug, Large\",Home,1,20,\n"
                + "ORD-2,2026-01-11,C2,West,RETURNED,L3,Shirt,Clothing,1,9.99,0\n"));

        assertThat(dataset.orders()).hasSize(2);
        assertThat(dataset.lineItems()).hasSize(3);
        assertThat(dataset.orders().getFirst().status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(dataset.lineItems().get(1).productName()).isEqualTo("Mug, Large");
        assertThat(dataset.lineItems().get(1).unitPrice()).isEqualByComparingTo("20.00");
        assertThat(dataset.lineItems().get(1).discountPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsMissingColumns() {
        assertThatThrownBy(() -> parser.parse(new StringReader("order_id,order_date\nORD-1,2026-01-01\n")))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getErrors().getFirst())
                        .contains("Missing required column").contains("customer_id"));
    }

    @Test
    void reportsEveryInvalidFieldWithLineNumbers() {
        assertThatThrownBy(() -> parser.parse(new StringReader(HEADER
                + "ORD-1,2026-13-40,C1,East,SHIPPED,L1,Widget,Electronics,0,-5,150\n")))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getErrors())
                        .anyMatch(m -> m.startsWith("Line 2: order_date"))
                        .anyMatch(m -> m.contains("status must be COMPLETED, CANCELLED or RETURNED"))
                        .anyMatch(m -> m.contains("quantity must be a whole number greater than 0"))
                        .anyMatch(m -> m.contains("unit_price must be a non-negative amount"))
                        .anyMatch(m -> m.contains("discount_percent must be between 0 and 100")));
    }

    @Test
    void rejectsConflictingOrderAttributesAcrossRows() {
        assertThatThrownBy(() -> parser.parse(new StringReader(HEADER
                + "ORD-1,2026-01-10,C1,East,COMPLETED,L1,Widget,Electronics,1,10,0\n"
                + "ORD-1,2026-01-10,C1,East,CANCELLED,L2,Mug,Home,1,10,0\n")))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getErrors().getFirst())
                        .contains("Line 3").contains("ORD-1"));
    }

    @Test
    void rejectsDuplicateLineItemIds() {
        assertThatThrownBy(() -> parser.parse(new StringReader(HEADER
                + "ORD-1,2026-01-10,C1,East,COMPLETED,L1,Widget,Electronics,1,10,0\n"
                + "ORD-2,2026-01-11,C2,East,COMPLETED,L1,Mug,Home,1,10,0\n")))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getErrors().getFirst())
                        .contains("duplicate line_item_id"));
    }

    @Test
    void rejectsFileWithNoDataRows() {
        assertThatThrownBy(() -> parser.parse(new StringReader(HEADER)))
                .isInstanceOf(CsvImportException.class)
                .satisfies(e -> assertThat(((CsvImportException) e).getErrors()).containsExactly("The file contains no data rows"));
    }
}
