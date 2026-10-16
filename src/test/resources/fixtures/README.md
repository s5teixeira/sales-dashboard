# Test fixture: small_sales.csv

Hand-calculated expected values (used by `DashboardServiceTest`):

| Order | Status    | Lines (net = qty x price x (1 - disc/100))       | Net     |
|-------|-----------|--------------------------------------------------|---------|
| ORD-1 | COMPLETED | 2 x 100.00 @10% = 180.00; 1 x 20.00 = 20.00      | 200.00  |
| ORD-2 | COMPLETED | 3 x 10.00 = 30.00                                | 30.00   |
| ORD-3 | RETURNED  | 1 x 10.00 = 10.00                                | (excluded) |
| ORD-4 | CANCELLED | 1 x 100.00 = 100.00                              | (excluded) |
| ORD-5 | COMPLETED | 1 x 100.00 @25% = 75.00; 1 x 10.00 = 10.00       | 85.00   |
| ORD-6 | COMPLETED | 1 x 33.33 @15% = 28.3305 -> 28.33                | 28.33   |

- Revenue 343.33, completed orders 4, AOV 85.83, units 9
- Return rate = 1 returned / 5 non-cancelled = 20.00%
- Unique (purchasing) customers = 3
- Monthly: Jan 230.00, Feb 0.00 (-100%), Mar 85.00 (N/A: prior month zero), Apr 28.33 (-66.67%)
