import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates the fictional sample dataset in sample_data/sales_sample.csv.
 *
 * Run from the project root (no build needed):
 *   java sample_data/GenerateSampleData.java
 *
 * The result is exactly 200 orders and 346 line items. The generator is
 * seeded, so running it again produces the same file. All customers, orders
 * and products are fictional.
 *
 * Built-in business patterns (so the dashboard has real trends to show):
 *  - Seasonality: holiday peak in Nov/Dec, post-holiday dip in Jan/Feb,
 *    back-to-school bump in Aug/Sep.
 *  - Category mix shifts by month (Office peaks Aug/Sep/Jan, Clothing peaks
 *    in spring and holidays, Electronics grows through the year).
 *  - Deeper discounts in November (Black Friday) and late December.
 *  - Clothing has a noticeably higher return rate than other categories.
 *  - Regions differ in volume; a pool of repeat customers places multiple orders.
 */
public class GenerateSampleData {

    record Product(String name, String category, String price) {}

    static final List<Product> CATALOG = List.of(
            new Product("Wireless Noise-Cancelling Headphones", "Electronics", "189.99"),
            new Product("4K Streaming Media Player", "Electronics", "59.99"),
            new Product("Smart Home Speaker", "Electronics", "99.00"),
            new Product("Portable Bluetooth Speaker", "Electronics", "74.50"),
            new Product("27-inch IPS Monitor", "Electronics", "249.00"),
            new Product("Fitness Tracker Watch", "Electronics", "129.95"),

            new Product("Stainless Steel Cookware Set", "Home & Kitchen", "159.00"),
            new Product("Programmable Coffee Maker", "Home & Kitchen", "84.99"),
            new Product("Chef's Knife 8-inch", "Home & Kitchen", "45.00"),
            new Product("Cotton Bath Towel Set", "Home & Kitchen", "39.99"),
            new Product("Air Fryer 5-Quart", "Home & Kitchen", "109.00"),
            new Product("Ceramic Dinnerware Set", "Home & Kitchen", "68.50"),

            new Product("Men's Merino Wool Sweater", "Clothing", "79.00"),
            new Product("Women's Waterproof Rain Jacket", "Clothing", "119.00"),
            new Product("Organic Cotton T-Shirt", "Clothing", "24.00"),
            new Product("Slim Fit Chino Pants", "Clothing", "54.99"),
            new Product("Running Shoes", "Clothing", "98.00"),
            new Product("Thermal Socks 3-Pack", "Clothing", "18.50"),

            new Product("Ergonomic Office Chair", "Office", "229.00"),
            new Product("Adjustable Standing Desk Converter", "Office", "179.99"),
            new Product("Wireless Keyboard and Mouse Combo", "Office", "49.99"),
            new Product("Hardcover Notebook 3-Pack", "Office", "21.00"),
            new Product("LED Desk Lamp", "Office", "36.75"),
            new Product("Gel Pen Set 24-Pack", "Office", "14.99"),

            new Product("Leather Bifold Wallet", "Accessories", "42.00"),
            new Product("Polarized Sunglasses", "Accessories", "65.00"),
            new Product("Laptop Backpack", "Accessories", "72.00"),
            new Product("Phone Case with Card Holder", "Accessories", "19.99"),
            new Product("USB-C Charging Cable 2-Pack", "Accessories", "15.99"),
            new Product("Canvas Tote Bag", "Accessories", "28.00"));

    static final String[] CATEGORIES = {"Electronics", "Home & Kitchen", "Clothing", "Office", "Accessories"};
    static final String[] REGIONS = {"Northeast", "Southeast", "Midwest", "Southwest", "West"};
    static final double[] REGION_WEIGHTS = {0.24, 0.19, 0.17, 0.14, 0.26};

    // Orders per month, Oct 2025 .. Sep 2026. These add up to exactly 200 orders.
    static final int[] ORDERS_PER_MONTH = {15, 23, 27, 12, 11, 14, 16, 16, 15, 14, 19, 18};

    // Category weights per month index (0 = Oct 2025). Order: Elec, Home, Cloth, Office, Acc.
    static double[] categoryWeights(int monthIndex) {
        double elecGrowth = 1.0 + monthIndex * 0.05; // electronics trend upward all year
        return switch (monthIndex) {
            case 1, 2 -> new double[]{0.34 * elecGrowth, 0.24, 0.22, 0.06, 0.14}; // Nov/Dec holidays
            case 3 -> new double[]{0.24 * elecGrowth, 0.18, 0.12, 0.32, 0.14};    // Jan: new-year office
            case 5, 6 -> new double[]{0.22 * elecGrowth, 0.22, 0.32, 0.10, 0.14}; // Mar/Apr: spring clothing
            case 10, 11 -> new double[]{0.24 * elecGrowth, 0.14, 0.18, 0.30, 0.14}; // Aug/Sep: back to school
            default -> new double[]{0.26 * elecGrowth, 0.24, 0.20, 0.14, 0.16};
        };
    }

    public static void main(String[] args) throws IOException {
        Random rnd = new Random(20251001L);
        Path out = Path.of("sample_data", "sales_sample.csv");

        // 110 customers; the first 25 are "loyal" and get picked more often.
        int customerCount = 110;
        String[] customerRegion = new String[customerCount + 1];
        for (int c = 1; c <= customerCount; c++) {
            customerRegion[c] = REGIONS[weightedIndex(rnd, REGION_WEIGHTS)];
        }

        List<String> lines = new ArrayList<>();
        lines.add("order_id,order_date,customer_id,region,status,line_item_id,product_name,category,quantity,unit_price,discount_percent");

        int orderSeq = 10001;
        int lineSeq = 1;
        YearMonth start = YearMonth.of(2025, 10);

        for (int m = 0; m < ORDERS_PER_MONTH.length; m++) {
            YearMonth ym = start.plusMonths(m);
            List<LocalDate> dates = new ArrayList<>();
            for (int i = 0; i < ORDERS_PER_MONTH[m]; i++) {
                dates.add(ym.atDay(1 + rnd.nextInt(ym.lengthOfMonth())));
            }
            dates.sort(null);

            for (LocalDate date : dates) {
                int customer = rnd.nextDouble() < 0.45 ? 1 + rnd.nextInt(25) : 26 + rnd.nextInt(customerCount - 25);
                String customerId = "CUST-%04d".formatted(customer);
                String region = customerRegion[customer];
                String orderId = "ORD-" + orderSeq++;

                int itemCount = pickItemCount(rnd);
                String primaryCategory = CATEGORIES[weightedIndex(rnd, categoryWeights(m))];
                List<Product> chosen = new ArrayList<>();
                boolean hasClothing = false;
                for (int i = 0; i < itemCount; i++) {
                    // Most items in an order share the primary category; some are cross-category.
                    String cat = (i == 0 || rnd.nextDouble() < 0.55)
                            ? primaryCategory
                            : CATEGORIES[weightedIndex(rnd, categoryWeights(m))];
                    Product p;
                    int guard = 0;
                    do {
                        p = pickFromCategory(rnd, cat);
                    } while (chosen.contains(p) && ++guard < 10);
                    if (chosen.contains(p)) continue;
                    chosen.add(p);
                    hasClothing |= p.category().equals("Clothing");
                }

                String status = pickStatus(rnd, hasClothing);
                for (Product p : chosen) {
                    BigDecimal price = new BigDecimal(p.price());
                    int qty = pickQuantity(rnd, price);
                    int discount = pickDiscount(rnd, date);
                    lines.add(String.join(",",
                            orderId, date.toString(), customerId, region, status,
                            "LI-%06d".formatted(lineSeq++), csv(p.name()), csv(p.category()),
                            String.valueOf(qty), price.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                            discount + ".00"));
                }
            }
        }

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            lines.forEach(w::println);
        }
        System.out.printf("Wrote %d orders / %d line items to %s%n", orderSeq - 10001, lineSeq - 1, out);
    }

    static int pickItemCount(Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.58) return 1;
        if (r < 0.85) return 2;
        if (r < 0.96) return 3;
        return 4;
    }

    static int pickQuantity(Random rnd, BigDecimal price) {
        boolean cheap = price.compareTo(new BigDecimal("40")) < 0;
        double r = rnd.nextDouble();
        if (cheap) return r < 0.45 ? 1 : r < 0.75 ? 2 : r < 0.92 ? 3 : 4;
        return r < 0.85 ? 1 : 2;
    }

    static int pickDiscount(Random rnd, LocalDate date) {
        double r = rnd.nextDouble();
        boolean blackFriday = date.getMonthValue() == 11 && date.getDayOfMonth() >= 20;
        boolean yearEnd = date.getMonthValue() == 12 && date.getDayOfMonth() >= 26;
        if (blackFriday || yearEnd) return r < 0.15 ? 10 : r < 0.55 ? 20 : 25;
        if (date.getMonthValue() == 11) return r < 0.4 ? 0 : r < 0.75 ? 10 : 15;
        return r < 0.68 ? 0 : r < 0.86 ? 5 : r < 0.96 ? 10 : 15;
    }

    static String pickStatus(Random rnd, boolean hasClothing) {
        double r = rnd.nextDouble();
        double cancelled = 0.06;
        double returned = hasClothing ? 0.16 : 0.06;
        if (r < cancelled) return "CANCELLED";
        if (r < cancelled + returned) return "RETURNED";
        return "COMPLETED";
    }

    static Product pickFromCategory(Random rnd, String category) {
        List<Product> inCat = CATALOG.stream().filter(p -> p.category().equals(category)).toList();
        // Skew toward the first products in each category so "top products" is meaningful.
        double[] weights = {0.26, 0.21, 0.18, 0.14, 0.12, 0.09};
        return inCat.get(weightedIndex(rnd, weights));
    }

    static int weightedIndex(Random rnd, double[] weights) {
        double total = 0;
        for (double w : weights) total += w;
        double r = rnd.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) return i;
        }
        return weights.length - 1;
    }

    static String csv(String value) {
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
