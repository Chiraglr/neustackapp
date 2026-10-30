#!/usr/bin/env python3
"""
Test 2: Concurrency & Pessimistic Locking (No Overselling)
==========================================================
Goal:
  Verify that when multiple concurrent requests attempt to purchase the
  exact same remaining stock at the same instant, pessimistic write locking
  ensures strictly NO overselling occurs, and stock never goes below zero.

How this tests it via external API:
  1. We check the current available inventory of a product (e.g. Product 2, stock = N).
  2. We prepare 2 * N distinct carts (twice as many carts as available stock).
     Each cart has quantity = 1.
  3. Using a Python ThreadPoolExecutor and threading.Barrier, we synchronize
     all worker threads so they all release their POST /orders checkout requests
     at the EXACT same millisecond.
  4. What MUST happen:
     - Exactly N checkouts succeed with HTTP 201.
     - Exactly N checkouts fail with HTTP 409 (Insufficient Inventory).
     - Final available inventory must be EXACTLY 0 (never negative).
  5. What triggers a bug:
     - If pessimistic write locking is missing or flawed, a race condition allows
       more than N checkouts to succeed (stock goes negative or orders exceed items sold).
"""

import sys
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests

BASE_URL = "http://localhost:8080"
TEST_PRODUCT_ID = 2  # Product 2 has seed stock (e.g., 10)

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
    print("  TEST 2: Concurrency & Lock Contention (Overselling Prevention)")
    print("═" * 70)

    initial_stock = get_inventory(TEST_PRODUCT_ID)
    info(f"Product {TEST_PRODUCT_ID} current stock: {initial_stock}")

    if initial_stock <= 0:
        fail(f"Product {TEST_PRODUCT_ID} has 0 stock. Run ./reset_db.sh first.")

    # We will spawn 2x threads competing for the available stock
    num_requests = initial_stock * 2
    info(f"Preparing {num_requests} separate carts competing for {initial_stock} units of stock...")

    cart_ids = []
    for _ in range(num_requests):
        r = requests.post(f"{BASE_URL}/carts", json={"product_id": TEST_PRODUCT_ID, "quantity": 1})
        if r.status_code != 201:
            fail(f"Failed to create cart during prep: HTTP {r.status_code} - {r.text}")
        cart_ids.append(r.json()["id"])

    ok(f"Successfully prepared {len(cart_ids)} carts.")

    # Barrier to force all threads to fire simultaneously
    barrier = threading.Barrier(num_requests)

    def checkout_worker(cid: str):
        # Wait until every worker is ready, then release all at once
        barrier.wait()
        res = requests.post(f"{BASE_URL}/orders", json={"cart_id": cid})
        return res.status_code, res.text

    info(f"Firing {num_requests} checkout requests concurrently via ThreadPoolExecutor...")
    results = []
    with ThreadPoolExecutor(max_workers=num_requests) as executor:
        futures = [executor.submit(checkout_worker, cid) for cid in cart_ids]
        for f in as_completed(futures):
            results.append(f.result())

    successes = [code for code, _ in results if code == 201]
    conflicts = [code for code, _ in results if code == 409]
    others = [code for code, text in results if code not in (201, 409)]

    info(f"Results summary:")
    info(f"  HTTP 201 (Orders created):      {len(successes)}")
    info(f"  HTTP 409 (Rejected - no stock): {len(conflicts)}")
    if others:
        info(f"  Other HTTP responses:           {len(others)}")

    # Check for overselling
    final_stock = get_inventory(TEST_PRODUCT_ID)
    info(f"Product {TEST_PRODUCT_ID} final inventory: {final_stock}")

    if len(successes) > initial_stock:
        fail(
            f"CONCURRENCY BUG DETECTED! OVERSELLING OCCURRED!\n"
            f"Available stock was: {initial_stock}, but {len(successes)} orders succeeded!\n"
            f"Final inventory is: {final_stock}."
        )

    if final_stock < 0:
        fail(f"CONCURRENCY BUG DETECTED! Inventory dropped below 0: {final_stock}")

    if len(successes) != initial_stock:
        fail(f"Expected exactly {initial_stock} orders to succeed, but got {len(successes)}.")

    if len(conflicts) != (num_requests - initial_stock):
        fail(f"Expected exactly {num_requests - initial_stock} conflicts, got {len(conflicts)}.")

    ok("Exactly the available stock was sold (no overselling) ✓")
    ok("Remaining requests were cleanly rejected with HTTP 409 Conflict ✓")
    ok("Final inventory reached exactly 0 without race conditions ✓")

    print("═" * 70)
    print("\033[92m  TEST 2 PASSED: Concurrency & pessimistic write locks verified!\033[0m")
    print("═" * 70)


if __name__ == "__main__":
    main()
