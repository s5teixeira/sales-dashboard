package com.s5teixeira.sales_dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.s5teixeira.sales_dashboard.TestData;
import com.s5teixeira.sales_dashboard.dto.DashboardData;
import com.s5teixeira.sales_dashboard.dto.Kpis;
import com.s5teixeira.sales_dashboard.dto.MonthlyRevenue;
import com.s5teixeira.sales_dashboard.dto.RegionPerformance;
import com.s5teixeira.sales_dashboard.model.SalesFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Verifies the SQL business rules against hand-calculated values (see fixtures/README.md). */
@SpringBootTest
class DashboardServiceTest {

    @Autowired
    DashboardService dashboardService;

    @Autowired
    DataImportService importService;

    @BeforeEach
    void loadFixture() {
        TestData.loadSmallFixture(importService);
    }

    @Test
    void kpisApplyDiscountsAndCountOnlyCompletedOrders() {
        Kpis k = dashboardService.dashboard(SalesFilter.none()).kpis();

        assertThat(k.totalRevenue()).isEqualByComparingTo("343.33");
        assertThat(k.completedOrders()).isEqualTo(4);
        assertThat(k.averageOrderValue()).isEqualByComparingTo("85.83");
        assertThat(k.unitsSold()).isEqualTo(9);
        assertThat(k.returnedOrders()).isEqualTo(1);
        assertThat(k.cancelledOrders()).isEqualTo(1);
        assertThat(k.nonCancelledOrders()).isEqualTo(5);
        assertThat(k.returnRatePct()).isEqualByComparingTo("20.00");
        assertThat(k.uniqueCustomers()).isEqualTo(3);
    }

    @Test
    void monthlySeriesFillsGapsAndHandlesZeroPriorMonth() {
        DashboardData data = dashboardService.dashboard(SalesFilter.none());
        List<MonthlyRevenue> m = data.monthly();

        assertThat(m).extracting(MonthlyRevenue::month).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));
        assertThat(m).extracting(MonthlyRevenue::revenue).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("230.00"), new BigDecimal("0.00"), new BigDecimal("85.00"), new BigDecimal("28.33"));
        assertThat(m.get(0).growthPct()).isNull();                       // no prior month
        assertThat(m.get(1).growthPct()).isEqualByComparingTo("-100.00");
        assertThat(m.get(2).growthPct()).isNull();                       // prior month revenue was zero
        assertThat(m.get(3).growthPct()).isEqualByComparingTo("-66.67");

        assertThat(data.kpis().monthlyGrowth().month()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(data.kpis().monthlyGrowth().growthPct()).isEqualByComparingTo("-66.67");
        assertThat(data.kpis().monthlyGrowth().partialMonth()).isFalse();
    }

    @Test
    void categoryFilterMeasuresOnlyThatCategoryButCountsWholeOrdersContainingIt() {
        Kpis electronics = dashboardService.dashboard(new SalesFilter(null, null, "Electronics", null)).kpis();
        assertThat(electronics.totalRevenue()).isEqualByComparingTo("255.00");   // 180 + 75, mug/shirt excluded
        assertThat(electronics.completedOrders()).isEqualTo(2);                   // ORD-1 and ORD-5
        assertThat(electronics.averageOrderValue()).isEqualByComparingTo("127.50");
        assertThat(electronics.returnRatePct()).isEqualByComparingTo("0.00");

        Kpis clothing = dashboardService.dashboard(new SalesFilter(null, null, "Clothing", null)).kpis();
        assertThat(clothing.totalRevenue()).isEqualByComparingTo("40.00");
        assertThat(clothing.completedOrders()).isEqualTo(2);
        assertThat(clothing.returnRatePct()).isEqualByComparingTo("33.33");      // 1 returned / 3 non-cancelled
    }

    @Test
    void dateAndRegionFiltersApplyToEveryMetric() {
        DashboardData west = dashboardService.dashboard(new SalesFilter(null, null, null, "West"));
        assertThat(west.kpis().totalRevenue()).isEqualByComparingTo("115.00");
        assertThat(west.regions()).extracting(RegionPerformance::region).containsExactly("West");

        DashboardData feb = dashboardService.dashboard(
                new SalesFilter(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), null, null));
        assertThat(feb.kpis().totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(feb.kpis().averageOrderValue()).isNull();                       // 0 completed orders -> N/A
        assertThat(feb.kpis().returnRatePct()).isEqualByComparingTo("100.00");
        assertThat(feb.monthly()).hasSize(1);
        assertThat(feb.kpis().monthlyGrowth().growthPct()).isNull();              // no prior month in range
    }

    @Test
    void emptyResultProducesNullRatiosInsteadOfMisleadingZeros() {
        DashboardData none = dashboardService.dashboard(
                new SalesFilter(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31), null, null));
        assertThat(none.kpis().totalRevenue()).isEqualByComparingTo("0");
        assertThat(none.kpis().averageOrderValue()).isNull();
        assertThat(none.kpis().returnRatePct()).isNull();
        assertThat(none.kpis().monthlyGrowth()).isNull();
        assertThat(none.monthly()).isEmpty();
        assertThat(none.categories()).isEmpty();
    }

    @Test
    void partialFinalMonthIsFlagged() {
        DashboardData data = dashboardService.dashboard(new SalesFilter(null, LocalDate.of(2026, 4, 10), null, null));
        assertThat(data.kpis().monthlyGrowth().partialMonth()).isTrue();
    }

    @Test
    void breakdownsRankByRevenueAndShareSumsTo100() {
        DashboardData data = dashboardService.dashboard(SalesFilter.none());

        assertThat(data.topProducts()).extracting("productName").containsExactly("Widget", "Mug", "Shirt");
        assertThat(data.categories().getFirst().category()).isEqualTo("Electronics");
        assertThat(data.categories().stream().map(c -> c.sharePct()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isBetween(new BigDecimal("99.98"), new BigDecimal("100.02"));
        assertThat(data.statuses()).extracting("status", "orders")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("COMPLETED", 4L),
                        org.assertj.core.groups.Tuple.tuple("RETURNED", 1L),
                        org.assertj.core.groups.Tuple.tuple("CANCELLED", 1L));
    }
}
