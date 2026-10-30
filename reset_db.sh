#!/usr/bin/env bash
# Reset the PostgreSQL database transactional state and restore seed inventory
# inside the running postgres container without restarting containers or dropping tables.

set -e

CONTAINER_NAME="postgres_db"
DB_NAME="ecommerce_db"
DB_USER="postgres"

echo "Checking if ${CONTAINER_NAME} is running..."
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}\$"; then
    echo "Error: Container '${CONTAINER_NAME}' is not running."
    echo "Start it using: docker compose up -d"
    exit 1
fi

echo "Resetting database '${DB_NAME}' data to initial seed state..."
docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB_NAME}" << 'EOF'
-- 1. Truncate transactional tables (orders, order_items, carts, cart_items, discount_coupons)
TRUNCATE TABLE order_items, orders, cart_items, carts, discount_coupons CASCADE;

-- 2. Restore seed inventory levels
UPDATE inventory SET available_inventory = 10 WHERE id BETWEEN 1 AND 7;
UPDATE inventory SET available_inventory = 1 WHERE id = 8;
EOF

echo ""
echo "Database reset successfully with initial seed data!"
echo "Note: If you have a data view open in pgAdmin, press F5 (or the Refresh button) to reload the view."
