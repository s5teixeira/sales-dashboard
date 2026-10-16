-- Orders: one row per customer order (order-level attributes)
CREATE TABLE IF NOT EXISTS orders (
    order_id     VARCHAR(20)  NOT NULL,
    order_date   DATE         NOT NULL,
    customer_id  VARCHAR(20)  NOT NULL,
    region       VARCHAR(40)  NOT NULL,
    status       VARCHAR(12)  NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT ck_orders_status CHECK (status IN ('COMPLETED', 'CANCELLED', 'RETURNED'))
);

-- Order items: one row per product line within an order
CREATE TABLE IF NOT EXISTS order_items (
    line_item_id      VARCHAR(20)    NOT NULL,
    order_id          VARCHAR(20)    NOT NULL,
    product_name      VARCHAR(120)   NOT NULL,
    category          VARCHAR(60)    NOT NULL,
    quantity          INT            NOT NULL,
    unit_price        DECIMAL(12, 2) NOT NULL,
    discount_percent  DECIMAL(5, 2)  NOT NULL DEFAULT 0,
    CONSTRAINT pk_order_items PRIMARY KEY (line_item_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE,
    CONSTRAINT ck_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_items_price CHECK (unit_price >= 0),
    CONSTRAINT ck_items_discount CHECK (discount_percent BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS idx_orders_date     ON orders (order_date);
CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_region   ON orders (region);
CREATE INDEX IF NOT EXISTS idx_items_order     ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_items_category  ON order_items (category);
