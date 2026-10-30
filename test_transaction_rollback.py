#!/usr/bin/env python3
"""
Test 1: Transaction Rollback Integrity (All-or-Nothing Atomicity)
================================================================
Goal:
  Verify that when a checkout fails mid-operation, the database transaction
  rolls back completely — preventing partial writes (phantom inventory deduction,
  partially created orders, or leaked state).

How this tests it via external API:
  1. We create a cart containing 2 items:
       - Item 1: Product 1 (sufficient inventory, e.g. 5 available, request 2).
       - Item 2: Product 8 (only 1 available, but we request 2).
  2. We call checkout POST /orders on this cart.
  3. Inside the checkout loop:
       - Product 1 inventory is evaluated first and deducted (10 -> 8).
       - Product 8 is evaluated next and throws InsufficientInventoryException (409 Conflict).
  4. If @Transactional is working properly:
       - Product 1's deduction MUST BE ROLLED BACK (remains at its original stock level).
       - No order or order items are persisted.
       - The cart is not associated with any order.
  5. If @Transactional is broken/missing:
       - Product 1's inventory will remain at 8 (a permanent inventory leak bug!).
"""

import sys
import requests

BASE_URL = "http://localhost:8080"
PASS_TAG = "\033[92m✓ PASS\033[0m"
FAIL_TAG = "\033[91m✗ FAIL\033[0m"
INFO_TAG = "\033[94mℹ\033[0m"


def ok(msg): print(f"  {PASS_TAG}  {msg}")
def fail(msg): print(f"  {FAIL_TAG}  {msg}"); sys.exit(1)
def info(msg): print(f"  {INFO_TAG}  {msg}")


def get_inventory(product_id: int) -> int:
    r = requests.post(f"{BASE_URL}/product_inventory_view", json={"product_ids": [product_id]})
    if r.status_code != 200:
        fail(f"Failed to fetch inventory for product {product_id}: HTTP {r.status_code}")
    items = r.json()
    return items[0]["available_inventory"]


def main():
    print("═" * 70)
    print("  TEST 1: Transaction Rollback Integrity (Mid-Flight Failure)")
    print("═" * 70)

    # 1. Check starting inventories
    # Product 1 has plenty of stock (e.g. 10). Product 8 has 1 in seed data.
    inv_prod1_initial = get_inventory(1)
    inv_prod8_initial = get_inventory(8)
    info(f"Initial Product 1 inventory: {inv_prod1_initial}")
    info(f"Initial Product 8 inventory: {inv_prod8_initial}")

    if inv_prod1_initial < 2:
        fail("Product 1 must have at least 2 units in stock before running test.")

    # 2. Create a cart with Product 1 (qty 2)
    info("Creating cart with Product 1 (qty 2)...")
    r = requests.post(f"{BASE_URL}/carts", json={"product_id": 1, "quantity": 2})
    if r.status_code != 201:
        fail(f"Failed to create cart: HTTP {r.status_code}")
    cart_id = r.json()["id"]
    ok(f"Cart created: {cart_id}")

    # 3. Add Product 8 with quantity exceeding its available stock (request 2 when stock is <= 1)
    excess_qty = inv_prod8_initial + 1
    info(f"Adding Product 8 to cart with excess quantity {excess_qty} (available: {inv_prod8_initial})...")
    # We directly create cart item via carts/{cart_id}/items if stock check allows,
    # or if cart addition checks stock, test with a cart where product 8 stock drops right before checkout.
    # Note: CartService.addItemToCart checks stock at add time, so let's verify:
    r_add = requests.post(f"{BASE_URL}/carts/{cart_id}/items", json={"product_id": 8, "quantity": inv_prod8_initial})
    if r_add.status_code != 200:
        fail(f"Failed to add product 8 to cart: HTTP {r_add.status_code} - {r_add.text}")
    ok(f"Product 8 added with qty {inv_prod8_initial}")

    # Now, simulate external stock depletion on Product 8 *before* this cart checks out:
    # Create Cart B, buy out Product 8 completely.
    info("Creating Cart B to buy out Product 8 before Cart A checks out...")
    r_cart_b = requests.post(f"{BASE_URL}/carts", json={"product_id": 8, "quantity": inv_prod8_initial})
    cart_b_id = r_cart_b.json()["id"]
    r_checkout_b = requests.post(f"{BASE_URL}/orders", json={"cart_id": cart_b_id})
    if r_checkout_b.status_code != 201:
        fail(f"Failed to buy out product 8 via Cart B: {r_checkout_b.text}")
    ok("Cart B completed. Product 8 inventory is now 0.")

    # Verify Product 8 is indeed 0 now
    inv_prod8_now = get_inventory(8)
    if inv_prod8_now != 0:
        fail(f"Product 8 inventory should be 0, but is {inv_prod8_now}")
    info("Product 8 inventory verified at 0.")

    # 4. Now attempt to checkout Cart A!
    # Cart A has:
    #   - Product 1 (qty 2) -> sufficient stock
    #   - Product 8 (qty 1) -> 0 stock available!
    info("Attempting checkout on Cart A (Product 1 has stock, Product 8 has 0)...")
    r_checkout_a = requests.post(f"{BASE_URL}/orders", json={"cart_id": cart_id})

    info(f"Checkout response status: HTTP {r_checkout_a.status_code}")
    if r_checkout_a.status_code != 409:
        fail(f"Expected HTTP 409 (Insufficient Inventory), but got HTTP {r_checkout_a.status_code}: {r_checkout_a.text}")
    ok("Checkout correctly failed with HTTP 409 Insufficient Inventory")

    # 5. ATOMICITY VERIFICATION:
    # Check if Product 1's inventory was rolled back or if it leaked/decremented!
    inv_prod1_after = get_inventory(1)
    info(f"Product 1 inventory after failed checkout: {inv_prod1_after} (Initial was: {inv_prod1_initial})")

    if inv_prod1_after != inv_prod1_initial:
        fail(
            f"TRANSACTION BUG DETECTED! Atomicity failed!\n"
            f"Product 1 inventory was deducted and NOT rolled back.\n"
            f"Expected: {inv_prod1_initial}, Actual: {inv_prod1_after} (Leaked: {inv_prod1_initial - inv_prod1_after} units)"
        )
    ok("Product 1 inventory remained completely unchanged (Transaction rollback succeeded!) ✓")

    # 6. Verify Cart A was not marked as checked out
    r_cart = requests.get(f"{BASE_URL}/carts/{cart_id}")
    cart_data = r_cart.json()
    if cart_data.get("order_id") is not None:
        fail(f"TRANSACTION BUG: Cart was assigned order_id {cart_data.get('order_id')} despite failed checkout!")
    ok("Cart has no order_id assigned ✓")

    print("═" * 70)
    print("\033[92m  TEST 1 PASSED: Transaction rollback is fully atomic!\033[0m")
    print("═" * 70)


if __name__ == "__main__":
    main()
