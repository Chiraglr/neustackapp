#!/usr/bin/env python3
"""
E-Commerce API Integration Test Suite
======================================
Tests three scenarios against a running Spring Boot service at BASE_URL.

Scenario 1 — Cart auto-deletion on empty:
  Create a cart with multiple items, remove all items one by one, then assert
  the cart no longer exists (expecting 404).

Scenario 2 — 20 orders, coupon usage, multi-product cycling, and inventory tracking:
  Create and checkout 20 orders (one item each, quantity 1).
  Cycles through products with available stock (products 1, 2, 3...) to respect
  the seed inventory limit of 10 units per product.
  If any active coupon is available, use it during checkout!
  After each order:
    - Assert the cart is deleted (404).
    - Assert that if a coupon was used, it is now marked as redeemed/unavailable.
    - Check coupon generation rules and inventory updates for that product after each order.
  At the end, call GET /admin/summary and validate all aggregate figures.

Scenario 3 — Admin manual coupon with duplicate code → 409:
  POST /admin/coupons with a code that already exists in the system
  (a coupon generated in Scenario 2 or seeded) — expect HTTP 409 Conflict.

Configuration
-------------
  BASE_URL                  : URL of the running Spring Boot app (default: http://localhost:8080)
  SCENARIO1_PRODUCT_IDS     : two product IDs used by Scenario 1 (must exist in DB)
  SCENARIO2_PRODUCT_IDS     : pool of product IDs to cycle through (e.g., [1, 2, 3])
  DISCOUNT_GENERATION_FACTOR: must match ecommerce.rewards.discount-generation-factor (default: 5)
"""

import sys
import requests

# ─────────────────────────────────────────────────────────────────────────────
# Configuration — adjust to match your seeded database
# ─────────────────────────────────────────────────────────────────────────────
BASE_URL = "http://localhost:8080"

SCENARIO1_PRODUCT_IDS = [1, 2]       # two products for the cart-deletion test
SCENARIO2_PRODUCT_IDS = [1, 2, 3]    # products cycled to satisfy 20 orders (each has 10 units in init.sql)

# Must match ecommerce.rewards.discount-generation-factor (default: 5)
DISCOUNT_GENERATION_FACTOR = 5

# ─────────────────────────────────────────────────────────────────────────────
# Pretty-print helpers
# ─────────────────────────────────────────────────────────────────────────────
PASS_TAG = "\033[92m✓ PASS\033[0m"
FAIL_TAG = "\033[91m✗ FAIL\033[0m"
INFO_TAG = "\033[94mℹ\033[0m"


def section(title: str):
    print(f"\n{'═' * 70}")
    print(f"  {title}")
    print(f"{'═' * 70}")


def ok(msg: str):
    print(f"  {PASS_TAG}  {msg}")


def fail(msg: str):
    print(f"  {FAIL_TAG}  {msg}")
    sys.exit(1)


def info(msg: str):
    print(f"  {INFO_TAG}  {msg}")


# ─────────────────────────────────────────────────────────────────────────────
# API wrappers
# ─────────────────────────────────────────────────────────────────────────────
def assert_status(resp: requests.Response, expected: int, context: str):
    if resp.status_code != expected:
        fail(
            f"{context}\n"
            f"         Expected HTTP {expected}, got {resp.status_code}\n"
            f"         Body: {resp.text[:600]}"
        )


def api_get_inventory(product_id: int) -> int:
    r = requests.post(
        f"{BASE_URL}/product_inventory_view",
        json={"product_ids": [product_id]},
    )
    assert_status(r, 200, f"POST /product_inventory_view (product {product_id})")
    items = r.json()
    if not items:
        fail(f"Product {product_id} not found in inventory view response.")
    return items[0]["available_inventory"]


def api_get_active_coupons() -> list:
    r = requests.get(f"{BASE_URL}/coupons")
    assert_status(r, 200, "GET /coupons")
    return r.json()


def api_get_admin_summary() -> dict:
    r = requests.get(f"{BASE_URL}/admin/summary")
    assert_status(r, 200, "GET /admin/summary")
    return r.json()


def api_create_cart(product_id: int, quantity: int = 1) -> dict:
    r = requests.post(
        f"{BASE_URL}/carts",
        json={"product_id": product_id, "quantity": quantity},
    )
    assert_status(r, 201, "POST /carts")
    return r.json()


def api_add_item(cart_id: str, product_id: int, quantity: int = 1) -> dict:
    r = requests.post(
        f"{BASE_URL}/carts/{cart_id}/items",
        json={"product_id": product_id, "quantity": quantity},
    )
    assert_status(r, 200, f"POST /carts/{cart_id}/items")
    return r.json()


def api_remove_item(cart_id: str, product_id: int) -> requests.Response:
    return requests.delete(f"{BASE_URL}/carts/{cart_id}/items/{product_id}")


def api_get_cart(cart_id: str) -> requests.Response:
    return requests.get(f"{BASE_URL}/carts/{cart_id}")


def api_checkout(cart_id: str, coupon_id: int | None = None) -> dict:
    payload: dict = {"cart_id": cart_id}
    if coupon_id is not None:
        payload["coupon_id"] = coupon_id
    r = requests.post(f"{BASE_URL}/orders", json=payload)
    assert_status(r, 201, f"POST /orders (cart {cart_id})")
    return r.json()


def api_admin_create_coupon(code: str, pct: int, max_limit: int) -> requests.Response:
    return requests.post(
        f"{BASE_URL}/admin/coupons",
        json={"code": code, "discount_percentage": pct, "max_limit": max_limit},
    )


# ─────────────────────────────────────────────────────────────────────────────
# Scenario 1 — Cart is auto-deleted when it becomes empty
# ─────────────────────────────────────────────────────────────────────────────
def scenario1_cart_auto_deletion():
    section("Scenario 1: Cart auto-deletion when emptied")

    pid_a, pid_b = SCENARIO1_PRODUCT_IDS[0], SCENARIO1_PRODUCT_IDS[1]

    # Create cart with product A
    info(f"Creating cart with product {pid_a}, qty=1 …")
    cart = api_create_cart(pid_a, quantity=1)
    cart_id = cart["id"]
    ok(f"Cart created — id={cart_id}")

    # Add product B
    info(f"Adding product {pid_b} to cart …")
    cart = api_add_item(cart_id, pid_b, quantity=1)
    if len(cart["items"]) != 2:
        fail(f"Expected 2 items in cart after adding second product, got {len(cart['items'])}")
    ok("Cart now has 2 items")

    # Confirm cart exists
    r = api_get_cart(cart_id)
    assert_status(r, 200, f"GET /carts/{cart_id} (should exist before removal)")
    ok("Cart confirmed to exist before any removals")

    # Remove items one by one
    for pid in [pid_a, pid_b]:
        info(f"Removing product {pid} …")
        r = api_remove_item(cart_id, pid)
        if r.status_code not in (200, 204, 404):
            fail(
                f"Unexpected HTTP {r.status_code} when removing product {pid}.\n"
                f"Body: {r.text[:400]}"
            )
        ok(f"  Product {pid} removed (HTTP {r.status_code})")

    # Assert cart is gone
    info("Verifying cart is deleted (expect 404) …")
    r = api_get_cart(cart_id)
    if r.status_code != 404:
        fail(
            f"Cart {cart_id} still exists after all items removed!\n"
            f"Expected HTTP 404, got {r.status_code}.\n"
            f"Body: {r.text[:400]}"
        )
    ok("Cart is gone — received expected 404 ✓")


# ─────────────────────────────────────────────────────────────────────────────
# Scenario 2 — 20 orders, coupon usage, product cycling, and inventory tracking
# ─────────────────────────────────────────────────────────────────────────────
def scenario2_twenty_orders():
    section("Scenario 2: 20 orders — coupon usage, product cycling & inventory tracking")

    product_pool  = SCENARIO2_PRODUCT_IDS
    num_orders    = 20
    qty_per_order = 1

    # ── Pre-run snapshots ────────────────────────────────────────────────────
    running_inventory = {}
    for pid in product_pool:
        inv = api_get_inventory(pid)
        running_inventory[pid] = inv
        info(f"Product {pid}: available inventory = {inv}")

    total_pool_capacity = sum(running_inventory.values())
    if total_pool_capacity < num_orders * qty_per_order:
        fail(
            f"Product pool {product_pool} only has {total_pool_capacity} total units — "
            f"need at least {num_orders * qty_per_order} for {num_orders} orders."
        )

    admin_before        = api_get_admin_summary()
    orders_before_count = admin_before["total_orders"]
    all_coupons_before  = admin_before["coupons_generated"]
    redeemed_before     = admin_before["coupons_redeemed"]
    gross_rev_before    = float(admin_before["gross_revenue"])
    net_rev_before      = float(admin_before["net_revenue"])
    discounts_before    = float(admin_before["total_discounts"])

    info(f"DB totals before run: orders={orders_before_count}, "
         f"coupons_ever={all_coupons_before}, redeemed={redeemed_before}")

    # Track metrics of this run to assert against final admin summary
    run_gross_total     = 0.0
    run_discount_total  = 0.0
    run_net_total       = 0.0
    run_coupons_used    = 0
    run_coupons_created = 0
    run_product_counts  = {pid: 0 for pid in product_pool}

    sim_order_count  = orders_before_count
    sim_coupon_count = all_coupons_before

    # ── Order loop (20 orders) ───────────────────────────────────────────────
    for i in range(1, num_orders + 1):
        # Pick product from pool with round-robin style, ensuring it has stock
        product_id = product_pool[(i - 1) % len(product_pool)]
        if running_inventory[product_id] < qty_per_order:
            # Fallback to any product with stock
            fallback = [pid for pid in product_pool if running_inventory[pid] >= qty_per_order]
            if not fallback:
                fail(f"All products in pool {product_pool} are out of stock before order {i}!")
            product_id = fallback[0]

        info(f"─── Order {i}/{num_orders} (Product ID: {product_id}) ───")

        # Check if an active coupon is available to use
        active_coupons = api_get_active_coupons()
        coupon_to_use = active_coupons[0] if active_coupons else None
        coupon_id = coupon_to_use["id"] if coupon_to_use else None

        if coupon_to_use:
            info(f"  Available coupon found: code={coupon_to_use['code']} (id={coupon_id}). Will apply to order.")
        else:
            info("  No active coupon available for this order.")

        # Create cart
        cart = api_create_cart(product_id, quantity=qty_per_order)
        cart_id = cart["id"]
        ok(f"  Cart created  id={cart_id}")

        # Checkout with coupon if available
        order = api_checkout(cart_id, coupon_id=coupon_id)
        order_id = order["order_id"]
        gross    = float(order["gross_amount"])
        discount = float(order["discount_amount"])
        net      = float(order["total_amount"])

        run_gross_total    += gross
        run_discount_total += discount
        run_net_total      += net
        run_product_counts[product_id] += qty_per_order

        if coupon_id is not None:
            run_coupons_used += 1
            ok(f"  Order placed with coupon! gross={gross:.2f}, discount={discount:.2f}, net={net:.2f}")
        else:
            ok(f"  Order placed without coupon. gross={gross:.2f}, discount={discount:.2f}, net={net:.2f}")

        # ── Assert: cart deleted after checkout ──────────────────────────────
        r = api_get_cart(cart_id)
        if r.status_code != 404:
            fail(
                f"  Cart {cart_id} still exists after checkout!\n"
                f"  Expected 404, got {r.status_code}."
            )
        ok("  Cart deleted after checkout ✓")

        # ── Assert: inventory decremented correctly ───────────────────────────
        expected_inv = running_inventory[product_id] - qty_per_order
        actual_inv   = api_get_inventory(product_id)
        if actual_inv != expected_inv:
            fail(
                f"  Inventory mismatch for product {product_id} after order {i}!\n"
                f"  Expected {expected_inv}, got {actual_inv}."
            )
        running_inventory[product_id] = actual_inv
        ok(f"  Product {product_id} inventory = {actual_inv} (expected {expected_inv}) ✓")

        # ── Assert: if coupon was used, verify it is no longer active ────────
        if coupon_id is not None:
            updated_active = api_get_active_coupons()
            active_ids = [c["id"] for c in updated_active]
            if coupon_id in active_ids:
                fail(f"  Coupon id={coupon_id} was used but still appears in active coupons!")
            ok(f"  Coupon id={coupon_id} successfully marked as redeemed ✓")

        # ── Assert: coupon generation milestone check ────────────────────────
        # Condition evaluated in service AFTER order and coupon update:
        # orderRepository.count() / discountGenerationFactor > discountCouponRepository.count()
        sim_order_count += 1
        expected_new_coupon = (sim_order_count // DISCOUNT_GENERATION_FACTOR) > sim_coupon_count
        if expected_new_coupon:
            sim_coupon_count += 1
            run_coupons_created += 1

        summary_now = api_get_admin_summary()
        actual_total_coupons = summary_now["coupons_generated"]
        expected_total_coupons = all_coupons_before + run_coupons_created
        if actual_total_coupons != expected_total_coupons:
            fail(
                f"  Coupon generation count mismatch after order {i}!\n"
                f"  Expected total coupons in DB: {expected_total_coupons}, got {actual_total_coupons}."
            )
        if expected_new_coupon:
            ok(f"  New reward coupon generated as expected after order {i} ✓ (total coupons: {actual_total_coupons})")
        else:
            ok(f"  No new coupon generated at order {i} ✓")

    # ── Admin summary validation at the end of 20 orders ─────────────────────
    section("Admin Summary Validation")
    info("Fetching final admin summary for aggregate validation …")
    summary = api_get_admin_summary()

    # Total orders
    total_orders = summary["total_orders"]
    expected_orders = orders_before_count + num_orders
    if total_orders != expected_orders:
        fail(
            f"Admin summary: total_orders={total_orders}, expected {expected_orders}."
        )
    ok(f"Admin summary: total_orders = {total_orders} (increased by {num_orders}) ✓")

    # Revenue checks
    gross_rev = float(summary["gross_revenue"])
    net_rev   = float(summary["net_revenue"])
    discounts = float(summary["total_discounts"])

    expected_gross = gross_rev_before + run_gross_total
    expected_net   = net_rev_before + run_net_total
    expected_disc  = discounts_before + run_discount_total

    if abs(gross_rev - expected_gross) > 0.05:
        fail(f"Admin summary: gross_revenue={gross_rev:.2f}, expected {expected_gross:.2f}")
    if abs(net_rev - expected_net) > 0.05:
        fail(f"Admin summary: net_revenue={net_rev:.2f}, expected {expected_net:.2f}")
    if abs(discounts - expected_disc) > 0.05:
        fail(f"Admin summary: total_discounts={discounts:.2f}, expected {expected_disc:.2f}")

    ok(f"Admin summary: gross={gross_rev:.2f}, net={net_rev:.2f}, discounts={discounts:.2f} match run deltas ✓")

    # gross - net must equal total_discounts
    if abs((gross_rev - net_rev) - discounts) > 0.02:
        fail(
            f"Admin summary: discount arithmetic incorrect.\n"
            f"  gross={gross_rev}  net={net_rev}  discounts={discounts}\n"
            f"  gross - net = {gross_rev - net_rev:.2f}"
        )
    ok("Admin summary: gross − net == total_discounts ✓")

    # Product-level quantity sold
    products_sold = {p["product_id"]: p["quantity_sold"] for p in summary["products_sold"]}
    for pid, qty_sold in run_product_counts.items():
        if qty_sold > 0:
            if pid not in products_sold:
                fail(f"Admin summary: product {pid} missing from products_sold.")
            reported_qty = products_sold[pid]
            if reported_qty < qty_sold:
                fail(
                    f"Admin summary: product {pid} quantity_sold={reported_qty}, "
                    f"expected at least {qty_sold}."
                )
            ok(f"Admin summary: product {pid} quantity_sold = {reported_qty} (≥ {qty_sold} in this run) ✓")

    # Coupon accounting: generated = available + redeemed
    gen    = summary["coupons_generated"]
    avail  = summary["coupons_available"]
    redeem = summary["coupons_redeemed"]
    if gen != avail + redeem:
        fail(
            f"Admin summary: coupons_generated ({gen}) ≠ "
            f"coupons_available ({avail}) + coupons_redeemed ({redeem})."
        )
    ok(f"Admin summary: coupons generated={gen}  available={avail}  redeemed={redeem} ✓")

    expected_redeemed = redeemed_before + run_coupons_used
    if redeem != expected_redeemed:
        fail(
            f"Admin summary: coupons_redeemed={redeem}, expected {expected_redeemed} "
            f"({redeemed_before} before + {run_coupons_used} used in this run)."
        )
    ok(f"Admin summary: redeemed count = {redeem} (tracked {run_coupons_used} used coupons) ✓")


# ─────────────────────────────────────────────────────────────────────────────
# Scenario 3 — POST /admin/coupons with a duplicate code → 409
# ─────────────────────────────────────────────────────────────────────────────
def scenario3_admin_coupon_duplicate():
    section("Scenario 3: POST /admin/coupons with duplicate code → 409 Conflict")

    # If there are active coupons, pick one; otherwise create one first
    active_coupons = api_get_active_coupons()
    if active_coupons:
        duplicate_code = active_coupons[0]["code"]
    else:
        # Create a coupon to duplicate
        duplicate_code = "SEEDED_COUPON_FOR_DUP_TEST"
        r_seed = api_admin_create_coupon(duplicate_code, pct=10, max_limit=100)
        assert_status(r_seed, 201, "POST /admin/coupons (seeding for duplicate test)")

    info(f"Attempting to create coupon with existing code: '{duplicate_code}' …")

    r = api_admin_create_coupon(duplicate_code, pct=15, max_limit=50)
    if r.status_code != 409:
        fail(
            f"Expected HTTP 409 Conflict for duplicate code '{duplicate_code}', "
            f"but got {r.status_code}.\n"
            f"Body: {r.text[:500]}"
        )
    ok(f"Duplicate code correctly rejected — HTTP 409 ✓")

    # Sanity-check: a genuinely unique code must still be accepted
    unique_code = "ADMIN_TEST_UNIQUE_XYZ_9999"
    info(f"Verifying a unique code ('{unique_code}') is accepted …")
    r2 = api_admin_create_coupon(unique_code, pct=5, max_limit=25)
    if r2.status_code != 201:
        fail(
            f"Creating new unique coupon '{unique_code}' failed.\n"
            f"Expected HTTP 201, got {r2.status_code}.\n"
            f"Body: {r2.text[:500]}"
        )
    ok(f"Unique code '{unique_code}' accepted — HTTP 201 ✓")


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────
def main():
    print(f"\n{'═' * 70}")
    print("  E-Commerce API Integration Tests")
    print(f"  Target : {BASE_URL}")
    print(f"  Discount generation factor : {DISCOUNT_GENERATION_FACTOR}")
    print(f"{'═' * 70}")

    # Connectivity check
    try:
        requests.get(f"{BASE_URL}/coupons", timeout=5)
    except requests.exceptions.ConnectionError:
        print(
            f"\n  ERROR: Cannot reach {BASE_URL}.\n"
            "  Make sure the Spring Boot service is running before running tests.\n"
        )
        sys.exit(1)

    scenario1_cart_auto_deletion()
    scenario2_twenty_orders()
    scenario3_admin_coupon_duplicate()

    print(f"\n{'═' * 70}")
    print("\033[92m  ALL SCENARIOS PASSED\033[0m")
    print(f"{'═' * 70}\n")


if __name__ == "__main__":
    main()
