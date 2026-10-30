#!/usr/bin/env python3
"""
Test 3: Checkout Idempotency & Duplicate Request Protection
===========================================================
Goal:
  Verify that when duplicate/concurrent checkout requests are submitted
  for the SAME cart_id (simulating network retries or rapid double-clicking):
  1. Only ONE order is created.
  2. Inventory is deducted only ONCE.
  3. Both concurrent callers receive the same order_id.

How this tests it via external API:
  Part A (Concurrent Race on Same Cart):
    - Create 1 cart with Product 3 (qty 1).
    - Record Product 3 initial stock.
    - Fire 5 CONCURRENT POST /orders requests for that SAME cart_id simultaneously
      using a thread barrier.
    - Assert:
      * Exactly 1 order is created (same order_id returned across all threads).
      * Product 3 inventory is deducted by ONLY 1 unit (not 5).
  Part B (Sequential Retry on Same Cart):
    - Send a follow-up checkout request on that same cart.
    - Assert it immediately returns the same order_id without re-deducting stock.
"""

import sys
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests

BASE_URL = "http://localhost:8080"
TEST_PRODUCT_ID = 3  # Product 3

PASS_TAG = "\033[92m✓ PASS\033[0m"
FAIL_TAG = "\033[91m✗ FAIL\033[0m"
INFO_TAG = "\033[94mℹ\033[0m"


def ok(msg): print(f"  {PASS_TAG}  {msg}")
def fail(msg): print(f"  {FAIL_TAG}  {msg}"); sys.exit(1)
def info(msg): print(f"  {INFO_TAG}  {msg}")


def get_inventory(product_id: int) -> int:
    r = requests.post(f"{BASE_URL}/product_inventory_view", json={"product_ids": [product_id]})
    if r.status_code != 200:
        fail(f"Failed to fetch inventory: HTTP {r.status_code}")
    items = r.json()
    return items[0]["available_inventory"]


def main():
    print("═" * 70)
    print("  TEST 3: Checkout Idempotency & Race Deduplication")
    print("═" * 70)

    # 1. Prepare product & cart
    initial_stock = get_inventory(TEST_PRODUCT_ID)
    info(f"Product {TEST_PRODUCT_ID} initial stock: {initial_stock}")
    if initial_stock < 1:
        fail(f"Product {TEST_PRODUCT_ID} has 0 stock. Run ./reset_db.sh first.")

    info(f"Creating a single cart with Product {TEST_PRODUCT_ID} (qty 1)...")
    r_cart = requests.post(f"{BASE_URL}/carts", json={"product_id": TEST_PRODUCT_ID, "quantity": 1})
    if r_cart.status_code != 201:
        fail(f"Cart creation failed: HTTP {r_cart.status_code} - {r_cart.text}")
    cart_id = r_cart.json()["id"]
    ok(f"Cart created: {cart_id}")

    # 2. PART A: Fire 5 simultaneous checkout requests on the SAME cart_id
    num_concurrent = 5
    info(f"Firing {num_concurrent} simultaneous checkout requests on the SAME cart ({cart_id})...")

    barrier = threading.Barrier(num_concurrent)

    def duplicate_checkout_worker():
        barrier.wait()
        res = requests.post(f"{BASE_URL}/orders", json={"cart_id": cart_id})
        return res.status_code, res.json() if res.status_code in (200, 201) else res.text

    responses = []
    with ThreadPoolExecutor(max_workers=num_concurrent) as executor:
        futures = [executor.submit(duplicate_checkout_worker) for _ in range(num_concurrent)]
        for f in as_completed(futures):
            responses.append(f.result())

    # Check HTTP response codes and order IDs
    order_ids = set()
    for status_code, body in responses:
        if status_code in (200, 201):
            order_ids.add(body.get("order_id"))
        else:
            fail(f"Unexpected error response during duplicate checkout: HTTP {status_code} - {body}")

    info(f"Distinct order IDs returned across {num_concurrent} concurrent calls: {order_ids}")

    if len(order_ids) != 1:
        fail(
            f"IDEMPOTENCY BUG DETECTED!\n"
            f"Expected exactly 1 order_id for the same cart, but got {len(order_ids)} distinct orders: {order_ids}"
        )
    committed_order_id = list(order_ids)[0]
    ok(f"All concurrent requests resolved to the EXACT same order_id: {committed_order_id} ✓")

    # 3. Check inventory deduction: must be deducted EXACTLY ONCE (initial_stock - 1)
    stock_after_race = get_inventory(TEST_PRODUCT_ID)
    info(f"Stock after {num_concurrent} concurrent checkouts: {stock_after_race} (Initial: {initial_stock})")

    expected_stock = initial_stock - 1
    if stock_after_race != expected_stock:
        fail(
            f"DOUBLE-DEDUCTION BUG DETECTED!\n"
            f"Stock should have decremented by exactly 1 (to {expected_stock}), "
            f"but actual stock is {stock_after_race} (deducted {initial_stock - stock_after_race} times!)"
        )
    ok("Inventory was decremented exactly once ✓")

    # 4. PART B: Sequential retry check
    info("Testing sequential retry on the already checked-out cart...")
    r_retry = requests.post(f"{BASE_URL}/orders", json={"cart_id": cart_id})
    if r_retry.status_code not in (200, 201):
        fail(f"Sequential retry failed: HTTP {r_retry.status_code} - {r_retry.text}")
    retry_order_id = r_retry.json().get("order_id")
    if retry_order_id != committed_order_id:
        fail(f"Sequential retry returned different order_id: expected {committed_order_id}, got {retry_order_id}")
    ok(f"Sequential retry returned same order_id: {retry_order_id} ✓")

    # Ensure stock still hasn't moved
    stock_final = get_inventory(TEST_PRODUCT_ID)
    if stock_final != expected_stock:
        fail(f"Stock changed on retry from {expected_stock} to {stock_final}!")
    ok(f"Stock remained unchanged at {stock_final} after sequential retry ✓")

    print("═" * 70)
    print("\033[92m  TEST 3 PASSED: Checkout idempotency and deduplication verified!\033[0m")
    print("═" * 70)


if __name__ == "__main__":
    main()
