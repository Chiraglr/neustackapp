-- 1. Products (Catalog)
CREATE TABLE IF NOT EXISTS products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    current_unit_price NUMERIC(10, 2) NOT NULL CHECK (current_unit_price > 0)
);

CREATE TABLE IF NOT EXISTS inventory (
    id INT PRIMARY KEY REFERENCES products(id),
    available_inventory INT NOT NULL CHECK (available_inventory > -1)
);

-- 2. Carts (Identified by a unique token/UUID, no user required)
CREATE TABLE IF NOT EXISTS carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Cart Items (Products inside a specific cart)
CREATE TABLE IF NOT EXISTS cart_items (
    cart_id UUID REFERENCES carts(id) ON DELETE CASCADE,
    product_id INT REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (cart_id, product_id)
);

-- 5. Coupons / Discounts
CREATE TABLE IF NOT EXISTS discount_coupons (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    discount_percentage INT NOT NULL,
    max_limit INT NOT NULL,
    is_used BOOLEAN DEFAULT FALSE
);

-- 4. Orders (Tracks completed checkouts to evaluate the "Every Nth order" rule)
CREATE TABLE IF NOT EXISTS orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    total_amount NUMERIC(10, 2) NOT NULL,
    discount_code_applied INT REFERENCES discount_coupons(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_items (
    order_id UUID REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id INT REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    purchased_unit_price NUMERIC(10, 2) NOT NULL CHECK (purchased_unit_price > 0),
    PRIMARY KEY (order_id, product_id)
);

-- --- SEED DATA ---
-- Seed initial store products so carts actually have items to add
INSERT INTO products (id, name, current_unit_price)
VALUES
    (1, 'Samsung', 1000),
    (2, 'OnePlus', 2000),
    (3, 'Apple 17', 3000),
    (4, 'Apple 18', 4000),
    (5, 'Macbook M1', 5000),
    (6, 'Macbook M2', 6000),
    (7, 'Macbook M5', 7000),
    (8, 'Macbook M5 Pro', 8000)
ON CONFLICT (id) DO NOTHING;
SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));

INSERT INTO inventory (id, available_inventory)
VALUES
    (1, 10),
    (2, 10),
    (3, 10),
    (4, 10),
    (5, 10),
    (6, 10),
    (7, 10),
    (8, 1)
ON CONFLICT (id) DO NOTHING;

----------------------------------

APIs

POST /product_inventory_view  {[product_ids...]} - status of products and inventory

GET /cart_view/{cart_id} - returns cart details, prices and totals

GET /create_cart/{product_id} - creates cart, add item, return cart_id(200), message if not available

GET /add_product_to_cart/{product_id}/{cart_id} - adds item, returns cart_id(200), message if not available

GET /remove_product_from_cart/{product_id}/{cart_id} - removes 1 item, returns 200, message if failed

***GET /checkout/{cart_id}/{coupon_id} - validates cart and creates an order, return order details(200), message if failed

GET /coupons - returns active coupons(200), message if failed

POST /generate_coupon - generate coupon

GET /admin_summary







